package com.portfolio.insurance.claim;

import com.portfolio.insurance.claim.ClaimDtos.ApproveClaimRequest;
import com.portfolio.insurance.claim.ClaimDtos.ClaimDetail;
import com.portfolio.insurance.claim.ClaimDtos.ClaimDocumentDto;
import com.portfolio.insurance.claim.ClaimDtos.ClaimSummary;
import com.portfolio.insurance.claim.ClaimDtos.HistoryEntry;
import com.portfolio.insurance.claim.ClaimDtos.ReportClaimRequest;
import com.portfolio.insurance.common.error.BusinessException;
import com.portfolio.insurance.common.error.ResourceNotFoundException;
import com.portfolio.insurance.common.plsql.DatabaseSideChanges;
import com.portfolio.insurance.common.web.PageResponse;
import com.portfolio.insurance.policy.Policy;
import com.portfolio.insurance.policy.PolicyRepository;
import com.portfolio.insurance.reference.City;
import com.portfolio.insurance.reference.ReferenceService;
import com.portfolio.insurance.user.AppUser;
import com.portfolio.insurance.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimService {

    /** Documents can no longer be added once a claim is closed. */
    private static final Set<ClaimStatus> CLOSED = EnumSet.of(ClaimStatus.REJECTED, ClaimStatus.PAID);

    private final ClaimRepository claimRepository;
    private final ClaimDocumentRepository documentRepository;
    private final PolicyRepository policyRepository;
    private final AppUserRepository userRepository;
    private final ReferenceService referenceService;
    private final ClaimLifecycle claimLifecycle;
    private final DocumentStorage documentStorage;
    private final DatabaseSideChanges databaseSideChanges;
    private final ClaimMapper claimMapper;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PageResponse<ClaimSummary> search(String query, ClaimStatus status, Long policyId, boolean onlyMine,
                                             String username, Pageable pageable) {
        return PageResponse.from(claimRepository
                .findAll(ClaimSpecifications.matching(query, status, policyId, onlyMine ? username : null), pageable)
                .map(claimMapper::toSummary));
    }

    public ClaimDetail findById(Long id) {
        Claim claim = getClaim(id);
        return claimMapper.toDetail(claim,
                claimMapper.toDocumentDtos(documentRepository.findByClaimIdOrderByIdAsc(id)), history(id));
    }

    /** Opens a claim (hasar ihbarı) in REPORTED status after checking coverage and duplicates. */
    @Transactional
    public ClaimDetail report(ReportClaimRequest request) {
        Policy policy = policyRepository.findDetailedById(request.policyId())
                .orElseThrow(() -> new ResourceNotFoundException("Poliçe", request.policyId()));
        LocalDate today = LocalDate.now(clock);
        Optional<String> notCovered = ClaimCoverage.check(policy.getStatus().name(), policy.getStartDate(),
                policy.getEndDate(), policy.getCancelledAt(), request.incidentDate(), today);
        if (notCovered.isPresent()) {
            throw new BusinessException("CLAIM_NOT_COVERED", notCovered.get());
        }
        String description = request.description().trim();
        if (claimRepository.existsDuplicate(policy.getId(), request.incidentDate(), description)) {
            throw new BusinessException("CLAIM_DUPLICATE", "Bu hasar ihbarı daha önce kaydedilmiş.");
        }
        City city = request.incidentCityId() == null ? null : referenceService.getCity(request.incidentCityId());

        Claim claim = Claim.report(claimRepository.nextId(), policy, request.incidentDate(), LocalDateTime.now(clock),
                city, description, request.estimatedAmount());
        claimRepository.saveAndFlush(claim);
        return findById(claim.getId());
    }

    /** The current claim handler takes over the file (REPORTED -> IN_REVIEW). */
    @Transactional
    public ClaimDetail assignToMe(Long id, String username) {
        AppUser handler = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı", username));
        databaseSideChanges.run(() -> claimLifecycle.assign(id, handler.getId(), username));
        return findById(id);
    }

    @Transactional
    public ClaimDetail approve(Long id, ApproveClaimRequest request, String username) {
        databaseSideChanges.run(() -> claimLifecycle.approve(id, request.approvedAmount(),
                request.note() == null ? null : request.note().trim(), username));
        return findById(id);
    }

    @Transactional
    public ClaimDetail reject(Long id, String note, String username) {
        databaseSideChanges.run(() -> claimLifecycle.reject(id, note.trim(), username));
        return findById(id);
    }

    @Transactional
    public ClaimDetail pay(Long id, String username) {
        databaseSideChanges.run(() -> claimLifecycle.pay(id, username));
        return findById(id);
    }

    @Transactional
    public ClaimDocumentDto addDocument(Long claimId, DocumentType type, MultipartFile file) {
        Claim claim = getClaim(claimId);
        if (CLOSED.contains(claim.getStatus())) {
            throw new BusinessException("CLAIM_CLOSED", "Kapanmış hasar dosyasına evrak eklenemez.");
        }
        DocumentStorage.StoredFile stored = documentStorage.store(claimId, file);
        String fileName = cleanFileName(file.getOriginalFilename());
        return claimMapper.toDto(documentRepository.save(new ClaimDocument(claim, type, fileName,
                stored.contentType(), stored.size(), stored.storagePath())));
    }

    public DocumentDownload document(Long claimId, Long documentId) {
        ClaimDocument document = documentRepository.findByIdAndClaimId(documentId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Evrak", documentId));
        return new DocumentDownload(documentStorage.load(document.getStoragePath()), document.getFileName(),
                document.getContentType());
    }

    public record DocumentDownload(Resource resource, String fileName, String contentType) {
    }

    private Claim getClaim(Long id) {
        return claimRepository.findDetailedById(id).orElseThrow(() -> new ResourceNotFoundException("Hasar", id));
    }

    /** Status changes recorded by TRG_CLAIM_AUDIT. */
    private List<HistoryEntry> history(Long claimId) {
        return jdbc.query("""
                SELECT changed_at, changed_by, operation,
                       JSON_VALUE(old_values, '$.status') AS from_status,
                       JSON_VALUE(new_values, '$.status') AS to_status
                  FROM audit_log
                 WHERE table_name = 'CLAIM' AND record_id = ?
                   AND (operation <> 'UPDATE'
                        OR NVL(JSON_VALUE(old_values, '$.status'), '-') <> NVL(JSON_VALUE(new_values, '$.status'), '-'))
                 ORDER BY id
                """, (rs, rowNum) -> new HistoryEntry(rs.getObject("changed_at", LocalDateTime.class),
                rs.getString("changed_by"), rs.getString("operation"), status(rs.getString("from_status")),
                status(rs.getString("to_status"))), claimId);
    }

    private static ClaimStatus status(String value) {
        return value == null ? null : ClaimStatus.valueOf(value);
    }

    /** Keeps only the last path segment and printable characters of a client-supplied file name. */
    static String cleanFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "evrak";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
