# Veritabanı Şeması (ER Diyagramı)

Şema tamamen Flyway migration'ları ile yönetilir: `backend/src/main/resources/db/migration`.

```mermaid
erDiagram
    APP_USER ||--o{ POLICY : "sells (agent)"
    APP_USER ||--o{ CLAIM : "handles"
    CITY ||--o{ CUSTOMER : "lives in"
    CITY ||--o{ PROPERTY : "located in"
    CITY ||--o{ CLAIM : "incident city"
    PRODUCT ||--o{ TARIFF_PARAMETER : "priced by"
    PRODUCT ||--o{ POLICY : "type of"
    CUSTOMER ||--o{ VEHICLE : "owns"
    CUSTOMER ||--o{ PROPERTY : "owns"
    CUSTOMER ||--o{ POLICY : "holds"
    CUSTOMER ||--o{ NOTIFICATION : "receives"
    VEHICLE |o--o{ POLICY : "insured by"
    PROPERTY |o--o{ POLICY : "insured by"
    POLICY |o--o| POLICY : "renewed from"
    POLICY ||--o{ INSTALLMENT : "paid in"
    POLICY ||--o{ CLAIM : "claimed on"
    POLICY ||--o{ NOTIFICATION : "about"
    INSTALLMENT ||--o{ PAYMENT : "settled by"
    CLAIM ||--o{ CLAIM_DOCUMENT : "has"

    APP_USER {
        NUMBER id PK
        VARCHAR2 username UK
        VARCHAR2 password_hash
        VARCHAR2 full_name
        VARCHAR2 email UK
        VARCHAR2 role "ADMIN | AGENT | CLAIM_HANDLER"
        NUMBER active
    }
    CITY {
        NUMBER id PK
        NUMBER plate_code UK "1-81"
        VARCHAR2 name UK
        NUMBER risk_factor
    }
    PRODUCT {
        NUMBER id PK
        VARCHAR2 code UK "TRAFFIC | CASCO | HOME | HEALTH"
        VARCHAR2 name
        VARCHAR2 asset_type "VEHICLE | PROPERTY | PERSON"
        NUMBER base_premium
        NUMBER min_premium
        NUMBER tax_rate
        NUMBER max_installments
        NUMBER term_months
    }
    TARIFF_PARAMETER {
        NUMBER id PK
        NUMBER product_id FK
        VARCHAR2 param_code "DRIVER_AGE, NCD_STEP, VALUE_RATE..."
        VARCHAR2 param_key "categorical rows"
        NUMBER min_value "range rows"
        NUMBER max_value "range rows"
        NUMBER factor
        DATE valid_from
        DATE valid_to
    }
    CUSTOMER {
        NUMBER id PK
        VARCHAR2 customer_type "INDIVIDUAL | CORPORATE"
        VARCHAR2 national_id UK "TCKN"
        VARCHAR2 tax_number UK "VKN"
        VARCHAR2 first_name
        VARCHAR2 last_name
        VARCHAR2 company_name
        DATE birth_date
        NUMBER city_id FK
    }
    VEHICLE {
        NUMBER id PK
        NUMBER customer_id FK
        VARCHAR2 plate_number UK
        VARCHAR2 chassis_number UK
        VARCHAR2 brand
        VARCHAR2 model
        NUMBER model_year
        VARCHAR2 usage_type "PRIVATE | COMMERCIAL | TAXI"
        NUMBER vehicle_value
    }
    PROPERTY {
        NUMBER id PK
        NUMBER customer_id FK
        NUMBER city_id FK
        NUMBER building_year
        NUMBER gross_area_m2
        VARCHAR2 construction_type
        NUMBER property_value
    }
    POLICY {
        NUMBER id PK
        VARCHAR2 policy_number UK
        NUMBER product_id FK
        NUMBER customer_id FK
        NUMBER vehicle_id FK
        NUMBER property_id FK
        NUMBER agent_id FK
        VARCHAR2 status "QUOTE | ACTIVE | SUSPENDED | CANCELLED | EXPIRED | RENEWED"
        DATE start_date
        DATE end_date
        NUMBER ncd_step "1-8"
        NUMBER net_premium
        NUMBER tax_amount
        NUMBER gross_premium
        NUMBER installment_count
        NUMBER refund_amount
        NUMBER renewed_from_policy_id FK
    }
    INSTALLMENT {
        NUMBER id PK
        NUMBER policy_id FK
        NUMBER installment_no
        DATE due_date
        NUMBER amount
        VARCHAR2 status "PENDING | PAID | OVERDUE | CANCELLED"
        TIMESTAMP paid_at
    }
    PAYMENT {
        NUMBER id PK
        NUMBER installment_id FK
        NUMBER amount
        TIMESTAMP payment_date
        VARCHAR2 payment_method
        VARCHAR2 reference_no UK
    }
    CLAIM {
        NUMBER id PK
        VARCHAR2 claim_number UK
        NUMBER policy_id FK
        DATE incident_date
        TIMESTAMP reported_at
        VARCHAR2 status "REPORTED | IN_REVIEW | APPROVED | REJECTED | PAID"
        NUMBER estimated_amount
        NUMBER approved_amount
        NUMBER paid_amount
        NUMBER handler_id FK
    }
    CLAIM_DOCUMENT {
        NUMBER id PK
        NUMBER claim_id FK
        VARCHAR2 document_type
        VARCHAR2 file_name
        VARCHAR2 storage_path UK
    }
    NOTIFICATION {
        NUMBER id PK
        NUMBER customer_id FK
        NUMBER policy_id FK
        VARCHAR2 notification_type
        VARCHAR2 channel "EMAIL | SMS"
        VARCHAR2 status "PENDING | SENT | FAILED"
    }
    AUDIT_LOG {
        NUMBER id PK
        VARCHAR2 table_name
        NUMBER record_id
        VARCHAR2 operation "INSERT | UPDATE | DELETE"
        CLOB old_values "JSON"
        CLOB new_values "JSON"
        VARCHAR2 changed_by
        TIMESTAMP changed_at
    }
```

`AUDIT_LOG` bilinçli olarak FK içermez: silinen kayıtların izi de korunur. Faz 4'te poliçe ve hasar tablolarındaki trigger'lar bu tabloyu doldurur.

## Tasarım kararları

- **Anahtarlar:** Her tablonun `NUMBER(19)` surrogate PK'sı kendi sequence'ından `DEFAULT seq.NEXTVAL` ile beslenir. İş anahtarları (`policy_number`, `claim_number`, `plate_number`, TCKN/VKN) ayrıca `UNIQUE` kısıtlıdır.
- **Ortak kolonlar:** `created_at/by`, `updated_at/by` (auditing) ve `version` (JPA optimistic locking).
- **Oracle 19c uyumu:** Boolean alanlar `NUMBER(1)` + `CHECK (x IN (0,1))`. 23ai'ye özgü söz dizimi kullanılmadı; sigorta şirketlerinde yaygın sürümlerde de çalışır.
- **Teklif = poliçe:** Teklif ayrı tablo değildir; `QUOTE` durumundaki poliçedir. Poliçeleştirme durum geçişiyle olur, numara ve geçmiş korunur.
- **Yenileme zinciri:** `renewed_from_policy_id` ile eski dönem poliçeye bağlanır; eski poliçe `RENEWED` olur.
- **Hasarsızlık basamağı (NCD):** 1-8 arası, giriş basamağı 4. Katsayılar `TARIFF_PARAMETER` içinde (`NCD_STEP`).
- **Tarife tablosu:** Tek, generic bir tablo. Bir satır ya sayısal aralıktır (`min_value..max_value`, örn. sürücü yaşı) ya da kategorik anahtardır (`param_key`, örn. kullanım tipi). `valid_from/valid_to` ile tarife versiyonlanabilir. Faz 4'teki `PKG_PREMIUM` bu tabloyu okur.
- **Kısıtlar veritabanında:** Durum değerleri, tutar işaretleri, tarih sırası, TCKN/VKN biçimi, "iptal edilen poliçede iptal nedeni zorunlu", "ödenen taksitte ödeme zamanı zorunlu" gibi kurallar `CHECK` ile korunur. Uygulama katmanı atlansa bile veri tutarlı kalır.
- **Batch için indeksler:** `policy (status, end_date)` yenileme/süre sonu job'ları, `installment (status, due_date)` gecikme job'u için.

## Seed verisi

| Migration | İçerik |
|---|---|
| `V6__seed_reference_data` | 81 il (risk katsayısıyla), 4 ürün, tarife parametreleri, demo kullanıcılar |
| `V7__seed_demo_data` | 50 müşteri (42 bireysel, 8 kurumsal), araç/konutlar, 200 poliçe, taksit planları, tahsilatlar, hasarlar |

`V7` bir PL/SQL bloğudur; `DBMS_RANDOM` sabit seed ile çalışır, bu yüzden her temiz kurulumda aynı veri oluşur. Tarihler migration anına göre hesaplanır. Böylece süresi 30 gün içinde dolacak poliçeler ve vadesi geçmiş taksitler her zaman bulunur (Faz 5 batch demoları). TCKN/VKN değerleri geçerli kontrol hanesiyle üretilir.

Demo kullanıcılar (şifre: `Demo123!`): `admin` (ADMIN), `agent` ve `agent2` (AGENT), `claims` (CLAIM_HANDLER).
