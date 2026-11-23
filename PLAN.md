# Sigorta Poliçe Yönetim Sistemi — Geliştirme Planı

## Amaç

Sigorta sektörü için uçtan uca bir full stack uygulama. Aşağıdaki teknik alanların her biri projede açıkça
görünmeli:

| Alan | Projede nerede görünür |
|---|---|
| PL/SQL | Prim hesaplama package'ı, trigger'lar, bulk işlemler, raporlama view'ları |
| Java + Spring Boot | Backend REST API, katmanlı mimari, güvenlik, test |
| Angular | Yönetim paneli (poliçe, hasar, batch izleme ekranları) |
| Batch süreçleri | Spring Batch job'ları + izleme ekranı |
| Dokümantasyon ve süreç | Temiz mimari, dokümantasyon, testler, ADR'ler, düzenli commit geçmişi |

## Kapsam kısıtları

- **Sadece lokal çalışacak.** Sunucuya deploy yok, CI/CD deploy adımı yok.
- Oracle veritabanı Docker'da çalışır. Backend ve frontend ister lokalde (`mvnw` / `ng serve`), ister `docker compose` ile ayağa kalkar.
- Her faz sonunda GitHub'a push edilir.

## Teknoloji yığını

- **Java 21** (LTS) — makinede JDK 22 kurulu, `release 21` ile derlenir
- **Spring Boot 3.x** (en güncel stabil), Maven Wrapper (`mvnw`) — makinede global Maven yok
- Spring Web, Spring Data JPA, Spring Security (JWT), Spring Batch, Spring Validation, Spring Boot Actuator
- **Oracle Database Free** (`gvenzl/oracle-free` Docker imajı)
- **Flyway** — tablo, sequence, PL/SQL package, trigger, view hepsi migration ile
- MapStruct (DTO mapping), Lombok
- springdoc-openapi (Swagger UI)
- Test: JUnit 5, Mockito, **Testcontainers (Oracle)**, Spring Batch Test
- **Angular** (en güncel stabil), standalone components, Angular Material, reactive forms, `HttpClient` interceptor (JWT)
- Docker Compose: `oracle` + `backend` + `frontend`

## Repo yapısı (monorepo)

```
insurance-policy-management/
├── backend/                  # Spring Boot
│   ├── src/main/java/com/portfolio/insurance/
│   │   ├── config/
│   │   ├── security/
│   │   ├── customer/         # feature bazlı paketleme
│   │   ├── policy/
│   │   ├── premium/
│   │   ├── claim/
│   │   ├── payment/
│   │   ├── batch/
│   │   ├── report/
│   │   └── common/           # exception handler, base entity, audit
│   └── src/main/resources/db/migration/   # Flyway (V1__..., R__pkg_... )
├── frontend/                 # Angular
├── docker/                   # Oracle init script'leri vb.
├── docs/
│   ├── architecture.md       # mimari + diyagramlar (Mermaid)
│   ├── er-diagram.md
│   ├── plsql.md              # PL/SQL bileşenlerinin açıklaması
│   ├── batch-jobs.md
│   └── adr/                  # Architecture Decision Records
├── docker-compose.yml
├── .env.example
├── CLAUDE.md
├── PLAN.md
└── README.md
```

Kod, tablo, kolon, commit mesajları **İngilizce**. README Türkçe + İngilizce özet. Arayüz metinleri Türkçe.

## Alan modeli

- **Customer** — bireysel/kurumsal, TCKN/VKN (algoritmik doğrulama), iletişim, adres
- **Vehicle / Property** — kasko-trafik için araç, konut için mülk bilgisi
- **Product** — TRAFFIC, CASCO, HOME, HEALTH; ürün bazlı tarife parametreleri
- **Quote (Teklif)** → **Policy (Poliçe)** — durumlar: `QUOTE`, `ACTIVE`, `SUSPENDED`, `CANCELLED`, `EXPIRED`, `RENEWED`
- **Endorsement (Zeyilname)** — poliçe değişikliği (opsiyonel, son fazlarda)
- **Installment (Taksit)** — vade, tutar, durum (`PENDING`, `PAID`, `OVERDUE`)
- **Payment (Tahsilat)**
- **Claim (Hasar)** — ihbar, durum akışı (`REPORTED`, `IN_REVIEW`, `APPROVED`, `REJECTED`, `PAID`), evrak
- **User / Role** — `ADMIN`, `AGENT` (acente), `CLAIM_HANDLER` (hasar uzmanı)
- **AuditLog** — trigger ile doldurulur
- **Notification** — batch'lerin ürettiği bildirim kayıtları (e-posta gönderimi simüle)

## Fazlar

Her faz: ayrı branch (`feature/phase-N-kisa-ad`) → iş bitince test → `main`'e merge → push.
Her fazın sonunda **dur, kullanıcıya özet ver, onay almadan sonraki faza geçme.**

### Faz 0 — İskelet ve GitHub
- Monorepo klasör yapısı, `.gitignore` (Java, Node, IDE, `.env`)
- `docker-compose.yml` içinde Oracle Free (healthcheck, volume), `.env.example`
- Spring Boot projesi (Spring Initializr eşdeğeri, Maven Wrapper dahil), Oracle bağlantısı, Flyway, Actuator health
- Angular projesi, Angular Material, boş layout (sidenav + toolbar)
- README: proje amacı, gereksinimler, "nasıl çalıştırılır"
- `git init`, ilk commit, `gh repo create` ile **public** repo (oluşturmadan önce kullanıcıya repo adını ve görünürlüğü onaylat), push
- **Bitti kriteri:** `docker compose up oracle` + `./mvnw spring-boot:run` + `ng serve` çalışıyor, `/actuator/health` UP.

### Faz 1 — Veritabanı şeması
- Flyway migration'ları: tüm tablolar, PK/FK, index, sequence, check constraint
- Seed data migration'ı: ürünler, tarife parametreleri, demo kullanıcılar, ~50 müşteri, ~200 poliçe
- `docs/er-diagram.md` (Mermaid ER)
- **Bitti kriteri:** temiz veritabanında migration'lar sorunsuz koşuyor.

### Faz 2 — Kimlik doğrulama ve temel backend
- Spring Security + JWT (login, refresh), rol bazlı yetki
- Global exception handler (RFC 7807 `ProblemDetail`), validation, base entity + auditing
- Swagger UI
- Angular: login sayfası, auth guard, JWT interceptor, rol bazlı menü
- **Bitti kriteri:** üç rolle giriş yapılıyor, yetkisiz endpoint 403 dönüyor.

### Faz 3 — Müşteri ve poliçe modülleri
- Customer CRUD (sayfalama, filtreleme, TCKN/VKN doğrulama)
- Teklif oluştur → poliçeleştir akışı, poliçe iptali, poliçe detay
- Angular: müşteri listesi/formu, poliçe listesi/detay, teklif sihirbazı (stepper)
- Unit + integration testler (Testcontainers)
- **Bitti kriteri:** UI'dan müşteri açılıp teklif alınıp poliçe kesilebiliyor.

### Faz 4 — PL/SQL prim motoru ⭐ (projenin en önemli farkı)
- `PKG_PREMIUM` package'ı:
  - `calculate_premium(p_product, p_customer_id, p_asset_id, ...) RETURN NUMBER`
  - Yaş, araç değeri/yaşı, il risk katsayısı, hasarsızlık indirim basamağı, ürün tarifesi
  - `generate_installments(p_policy_id, p_count)` — taksit planı üretir
- `PKG_POLICY` — poliçe durum geçişleri, iptal halinde gün esaslı prim iadesi hesabı
- Trigger: poliçe/hasar tablolarında değişiklikleri `AUDIT_LOG`'a yazar
- Custom exception'lar (`RAISE_APPLICATION_ERROR`) → Java tarafında anlamlı hata mesajına çevrilir
- Java tarafı `SimpleJdbcCall` ile çağırır; servis katmanı arkasında soyutlanır
- PL/SQL için Testcontainers testleri (bilinen girdiler → beklenen prim)
- `docs/plsql.md`
- **Bitti kriteri:** teklif ekranındaki prim PL/SQL'den geliyor, testler geçiyor.

### Faz 5 — Spring Batch süreçleri ⭐
Job metadata tabloları Oracle'da. Her job için: reader/processor/writer, chunk-oriented, skip/retry politikası, listener ile loglama.
1. **policyRenewalJob** — 30 gün içinde süresi dolacak poliçeler için yenileme teklifi üretir (prim PL/SQL'den)
2. **overdueInstallmentJob** — vadesi geçen taksitleri `OVERDUE` yapar, 2+ gecikmede poliçeyi `SUSPENDED` yapar, bildirim kaydı açar
3. **policyExpirationJob** — süresi dolanları `EXPIRED` yapar
4. **claimImportJob** — CSV'den toplu hasar ihbarı içe aktarır; hatalı satırları ayrı hata dosyasına/tabloya yazar (skip limit)
5. **monthlyProductionReportJob** — aylık prim üretim raporunu Excel'e (Apache POI) yazar
- Zamanlama: `@Scheduled` + cron (properties'ten yönetilir, lokal demo için kapatılabilir)
- Performans: büyük veri için PL/SQL tarafında `BULK COLLECT` / `FORALL` kullanan bir prosedür, Java batch ile karşılaştırma notu `docs/batch-jobs.md`'de
- REST: job listesi, elle tetikleme, çalışma geçmişi
- Seed script: performans demosu için 50k+ poliçe üreten opsiyonel script
- Spring Batch Test ile job testleri
- **Bitti kriteri:** tüm job'lar elle tetiklenip başarıyla bitiyor, hatalı CSV satırları raporlanıyor.

### Faz 6 — Batch izleme ekranı (Angular)
- Job listesi, son çalışma durumu, süre, okunan/yazılan/atlanan kayıt sayısı
- Elle tetikleme butonu (parametre girişiyle), çalışma geçmişi, adım (step) detayı
- CSV yükleme ekranı (claimImportJob)
- **Bitti kriteri:** job UI'dan tetiklenip sonucu ekranda izlenebiliyor.

### Faz 7 — Hasar ve tahsilat modülleri
- Hasar ihbarı, evrak yükleme (lokal dosya sistemi), durum akışı, hasar uzmanı onay ekranı
- Taksit listesi, ödeme girişi (simülasyon)
- Hasar ödendiğinde poliçenin hasarsızlık basamağını güncelleyen PL/SQL prosedürü
- **Bitti kriteri:** ihbardan ödemeye kadar hasar akışı UI'dan yürütülebiliyor.

### Faz 8 — Raporlama ve dashboard
- Oracle view / materialized view: ürün bazlı prim üretimi, hasar/prim oranı (loss ratio), acente performansı
- Angular dashboard: KPI kartları + grafikler (ngx-charts veya Chart.js)
- **Bitti kriteri:** dashboard gerçek veriden besleniyor.

### Faz 9 — Kalite ve vitrin
- Test kapsamı (JaCoCo raporu), eksik testler
- GitHub Actions: sadece **build + test** (deploy yok)
- Tam `docker compose up` ile tek komutta çalışma
- README son hali: ekran görüntüleri, mimari diyagram, özellik listesi, "teknik kapsam" tablosu, demo kullanıcı bilgileri
- `docs/adr/` altında 3-4 karar kaydı (neden PL/SQL'de prim, neden Spring Batch, neden feature paketleme...)
- **Bitti kriteri:** temiz bir makinede README'yi takip eden biri projeyi 10 dakikada ayağa kaldırabiliyor.

### Faz 10 — Banka tahsilat dosyası entegrasyonu (Spring Batch, ileri seviye)
Sigorta şirketleri prim tahsilatını bankalarla sabit uzunluklu (positional) dosya alışverişiyle yapar (otomatik ödeme talimatı). Bu faz o akışı simüle eder ve Spring Batch'in ileri özelliklerini gösterir. Dosya formatı kurgusaldır, gerçek bir bankanın formatı kopyalanmaz.
- **Format:** `docs/bank-file-format.md` içinde kurgusal bir format tanımla: header kaydı (banka kodu, dosya tarihi), detay kayıtları (poliçe no, taksit no, tutar, işlem sonucu kodu, sabit uzunluklu alanlar), trailer kaydı (kayıt sayısı, toplam tutar kontrolü).
- **collectionRequestExportJob (giden dosya):** vadesi gelen `PENDING` taksitlerden bankaya gönderilecek tahsilat talep dosyasını üretir (`FlatFileItemWriter` + `FormatterLineAggregator`). Header ve trailer callback'leri kullanılır.
- **collectionResultImportJob (gelen dosya):** bankadan dönen sonuç dosyasını okur (`FixedLengthTokenizer`, `PatternMatchingCompositeLineMapper` ile header/detail/trailer ayrımı).
  - Trailer doğrulaması: kayıt sayısı ve toplam tutar tutmazsa job `FAILED` olur, hiçbir kayıt işlenmez.
  - Başarılı satırlar `PKG_PAYMENT` üzerinden tahsil edilir. Reddedilenler (yetersiz bakiye, kapalı hesap...) taksit üzerinde sebep koduyla işaretlenir ve bildirim kaydı açılır.
  - Idempotency: aynı dosya iki kez yüklenirse ikinci yükleme reddedilir (dosya hash'i + `JobParameters`).
- **Ölçek:** 1M+ satırlık sonuç dosyası üreten test verisi script'i. Partitioning (`Partitioner` ile satır aralıkları) ve multi-threaded step; tek thread ile süre karşılaştırması `docs/batch-jobs.md`'ye yazılır.
- **Restart:** job ortada öldürülürse (ör. belirli bir satırda yapay hata bayrağı) yeniden başlatıldığında kaldığı chunk'tan devam eder, çift tahsilat oluşmaz. Bunu kanıtlayan integration test yazılır.
- **Dosya izleme:** lokal bir `inbox/` klasörüne bırakılan dosyayı otomatik algılayıp job'u tetikleyen basit poller (Spring Integration file inbound adapter veya `@Scheduled` tarama); işlenen dosya `processed/`, hatalı dosya `error/` klasörüne taşınır.
- **Angular:** tahsilat dosyaları ekranı: yüklenen/üretilen dosyalar, durum, başarılı/reddedilen satır sayısı, red sebepleri dağılımı, dosya indirme.
- **Bitti kriteri:** talep dosyası üretiliyor, sonuç dosyası yüklenince taksitler doğru güncelleniyor; trailer hatası, mükerrer dosya ve yarıda kesilip restart senaryoları testlerle kanıtlanıyor.

## Kapsam dışı
- Sunucu/bulut deploy, domain, HTTPS
- Gerçek ödeme entegrasyonu, gerçek e-posta/SMS gönderimi
- Mikroservis bölünmesi (tek modüler monolit yeterli ve daha okunur)
