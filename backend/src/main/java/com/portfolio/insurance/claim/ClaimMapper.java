package com.portfolio.insurance.claim;

import com.portfolio.insurance.claim.ClaimDtos.ClaimDetail;
import com.portfolio.insurance.claim.ClaimDtos.ClaimDocumentDto;
import com.portfolio.insurance.claim.ClaimDtos.ClaimSummary;
import com.portfolio.insurance.claim.ClaimDtos.HistoryEntry;
import com.portfolio.insurance.policy.Policy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ClaimMapper {

    @Mapping(target = "policyId", source = "policy.id")
    @Mapping(target = "policyNumber", source = "policy.policyNumber")
    @Mapping(target = "productName", source = "policy.product.name")
    @Mapping(target = "customerName", source = "policy.customer.displayName")
    @Mapping(target = "handlerName", source = "handler.fullName")
    ClaimSummary toSummary(Claim claim);

    @Mapping(target = "id", source = "claim.id")
    @Mapping(target = "version", source = "claim.version")
    @Mapping(target = "createdBy", source = "claim.createdBy")
    @Mapping(target = "status", source = "claim.status")
    @Mapping(target = "policyId", source = "claim.policy.id")
    @Mapping(target = "policyNumber", source = "claim.policy.policyNumber")
    @Mapping(target = "productName", source = "claim.policy.product.name")
    @Mapping(target = "policyStatus", source = "claim.policy.status")
    @Mapping(target = "ncdStep", source = "claim.policy.ncdStep")
    @Mapping(target = "customerId", source = "claim.policy.customer.id")
    @Mapping(target = "customerName", source = "claim.policy.customer.displayName")
    @Mapping(target = "insuredObject", expression = "java(insuredObject(claim.getPolicy()))")
    @Mapping(target = "incidentCityName", source = "claim.incidentCity.name")
    @Mapping(target = "handlerId", source = "claim.handler.id")
    @Mapping(target = "handlerName", source = "claim.handler.fullName")
    @Mapping(target = "handlerUsername", source = "claim.handler.username")
    ClaimDetail toDetail(Claim claim, List<ClaimDocumentDto> documents, List<HistoryEntry> history);

    ClaimDocumentDto toDto(ClaimDocument document);

    List<ClaimDocumentDto> toDocumentDtos(List<ClaimDocument> documents);

    default String insuredObject(Policy policy) {
        if (policy.getVehicle() != null) {
            return policy.getVehicle().getDescription();
        }
        if (policy.getProperty() != null) {
            return policy.getProperty().getDescription();
        }
        return policy.getCustomer().getDisplayName();
    }
}
