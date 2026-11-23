# Batch Süreçleri

Periyodik iş süreçleri Spring Batch 6 job'larıdır. Job metadata tabloları (`BATCH_*`) Oracle'dadır ve Flyway
migration'ı ile oluşturulur (`V10__create_spring_batch_tables.sql`). Boot'un şema oluşturması kapalıdır.

| Job | Ne yapar | Parametre | Varsayılan cron |
|---|---|---|---|
| `policyRenewalJob` | 30 gün içinde süresi dolacak poliçelere yenileme teklifi üretir (prim PL/SQL'den) | `runDate` | her gün 01:30 |
| `overdueInstallmentJob` | Vadesi geçen taksitleri `OVERDUE` yapar, 2+ gecikmede poliçeyi `SUSPENDED` yapar, bildirim açar | `runDate` | her gün 01:00 |
| `overdueInstallmentPlsqlJob` | Aynı işi tek PL/SQL çağrısında (`BULK COLLECT` / `FORALL`) yapar | `runDate` | — |
| `policyExpirationJob` | Süresi dolanları kapatır: yenilenmiş olan `RENEWED`, diğerleri `EXPIRED` | `runDate` | her gün 01:15 |
| `claimImportJob` | CSV'den toplu hasar ihbarı alır, hatalı satırları raporlar | `inputFile` | — (dosya yüklemesiyle) |
| `monthlyProductionReportJob` | Aylık prim üretimini ürün/acente bazında Excel'e yazar | `month` (yyyy-MM) | her ayın 1'i 06:00 |

Parametre verilmezse `runDate` bugündür, `month` bir önceki aydır. Her manuel çalıştırmaya `launchedAt` parametresi
eklenir; böylece aynı tarihle job tekrar çalıştırılabilir. Job'lar idempotent yazılmıştır: yenileme teklifi olan
poliçe tekrar okunmaz, `PENDING` olmayan taksit tekrar işaretlenmez.

## Ortak yapı

Her chunk-oriented step aynı kalıbı izler:

```
JdbcCursorItemReader ──► ItemProcessor ──► ItemWriter (JDBC batch insert/update)
        │                                          │
        └── @StepScope, jobParameters['runDate']   └── bildirimler: NotificationWriter
```

- **Chunk:** `app.batch.chunk-size` (100). Her chunk tek transaction'dır.
- **Retry:** `TransientDataAccessException` (bağlantı kopması, deadlock) 3 kez yeniden denenir.
- **Skip:** İş kuralı hataları (`BusinessException`, ör. tarifesi bulunamayan yenileme) ve geçersiz CSV
  satırları atlanır, `app.batch.skip-limit` (100) aşılırsa step başarısız olur.
- **Listener:** `JobLoggingListener` her job ve step için okunan/yazılan/atlanan kayıt sayısını loglar. Kayıt atlayan
  step'in çıkış kodunu `COMPLETED_WITH_SKIPS` yapar; izleme ekranı bunu ayrıca gösterir.
- **Durum değişiklikleri** her zaman `PKG_POLICY.CHANGE_STATUS` üzerinden yapılır. Geçiş kuralları tek yerdedir:
  askıdaki bir poliçe `RENEWED` olamaz, iptal edilmiş poliçe süre sonuna girmez.
- **Bildirimler** `NOTIFICATION` tablosuna `PENDING` olarak yazılır (gönderim simülasyonu). E-postası olan müşteriye
  `EMAIL`, yoksa telefonuna `SMS`, ikisi de yoksa bildirim yazılmaz.

> **Batch 6 notu:** Yeni `ChunkOrientedStepBuilder`'da `retry()`/`skip()` tek başına yetmez; `.faultTolerant()`
> çağrılmazsa istisna atlanmaz ve step düşer. Bu, testlerde CSV import job'ının ilk denemesinde yakalandı.

## Job'lar

### policyRenewalJob

1. **Reader:** `ACTIVE`, bitişi `runDate .. runDate + 30 gün` arasında olan, henüz yenileme teklifi olmayan
   poliçeler. Bir sonraki dönemin hasarsızlık basamağı SQL içinde `PKG_POLICY.NEXT_NCD_STEP` ile hesaplanır:
   hasarsız dönem +1, ödenmiş her hasar −1 (1..8).
2. **Processor:** `PremiumEngine` → `PKG_PREMIUM.CALCULATE`. Yeni dönem eski dönemin bittiği gün başlar.
3. **Writer:** `QUOTE` durumunda poliçe (`renewed_from_policy_id` = eski poliçe, teklif geçerliliği = eski bitiş
   tarihi) ve `RENEWAL_OFFER` bildirimi.

Teklif poliçeleştirildiğinde eski poliçe dönem sonunda `policyExpirationJob` tarafından `RENEWED` yapılır.

### overdueInstallmentJob

| Step | Reader | Writer |
|---|---|---|
| `markOverdueStep` | `PENDING` ve vadesi `runDate`'ten önce olan taksitler (poliçe `ACTIVE`/`SUSPENDED`) | `OVERDUE` + `INSTALLMENT_OVERDUE` bildirimi |
| `suspendPoliciesStep` | En az `app.batch.suspend-after-overdue` (2) gecikmiş taksiti olan `ACTIVE` poliçeler | `PKG_POLICY.CHANGE_STATUS(...,'SUSPENDED')` + `POLICY_SUSPENDED` bildirimi |

### policyExpirationJob

`ACTIVE`/`SUSPENDED` olup bitişi `runDate`'ten önce olan poliçeler. Yenileme poliçesi düzenlenmişse (teklif değil)
ve eski poliçe aktifse `RENEWED`, diğer durumlarda `EXPIRED` + `POLICY_EXPIRED` bildirimi.

### claimImportJob

CSV dosyası UTF-8, `;` ayraçlı ve başlık satırlıdır (Türkçe Excel'in varsayılan biçimi):

```csv
policy_number;incident_date;description;estimated_amount;incident_city_plate
TRF-2026-000001;05.10.2026;Kavşakta arkadan çarpma;38.500,00;34
KSK-2026-000003;2026-11-01;"Otoparkta çizilme";12500.75;
```

- Tarih `gg.aa.yyyy` veya `yyyy-aa-gg` olabilir. Doğrulama katıdır: `31.11.2026` reddedilir.
- Tutar ondalık virgül veya nokta kabul eder (`12.500,50`, `12500.50`).
- İl plaka kodu isteğe bağlıdır. Tırnak içindeki alanlar desteklenir.

Satır şu durumlarda reddedilir: alan sayısı yanlış, poliçe yok ya da teklif aşamasında, hasar tarihi gelecekte ya
da teminat süresi dışında (iptal edilmiş poliçede iptal tarihine kadar), tutar 0 veya negatif, ihbar daha önce
kaydedilmiş (aynı poliçe + tarih + açıklama; aynı dosya içinde de kontrol edilir).

Reader satırı ham haliyle okur, ayrıştırma processor'da yapılır. Böylece hatalı satır, satır numarası ve
içeriğiyle `CLAIM_IMPORT_ERROR` tablosuna yazılabilir (`ClaimImportErrorRecorder`, bir `SkipListener`).
Geçerli satırlar `REPORTED` durumunda `CLAIM` tablosuna girer; audit trigger her ihbarı kaydeder.

Örnek dosya: [`docs/samples/hasar-ihbar-ornek.csv`](samples/hasar-ihbar-ornek.csv). Temiz kurulumda 3 satır içe
alınır, 4 satır raporlanır. Poliçe numaraları seed verisinden gelir; seed tarihleri kurulum anına göre
hesaplandığından, numaralar ileride farklılaşırsa dosyayı güncelleyin.

### monthlyProductionReportJob

Ay içinde tanzim edilen (`issued_at`) poliçeler ürün ve acente bazında gruplanır: poliçe ve iptal adedi, net prim,
vergi, brüt prim, iade. Writer bir `ItemStreamWriter`'dır:

- Apache POI `SXSSFWorkbook` kullanır; bellekte sadece 100 satırlık bir pencere tutar.
- Step kapanırken toplam satırını ekler.
- Dosyayı önce geçici bir isimle yazar, sonra `app.batch.report-dir/uretim-raporu-yyyy-MM.xlsx` olarak taşır.
  Yarıda kalan bir çalışma bozuk rapor bırakmaz.

## REST API

Tüm uçlar `ADMIN` rolü gerektirir. Manuel çalıştırmalar asenkrondur: istek, çalıştırma oluşturulur oluşturulmaz
`202 Accepted` ile döner, durum sorgulanarak izlenir.

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/api/batch/jobs` | Job listesi, açıklama, cron ve son çalışma |
| POST | `/api/batch/jobs/{job}/executions` | Çalıştır; gövde `{"parameters":{"runDate":"2026-11-23"}}` (isteğe bağlı) |
| GET | `/api/batch/jobs/{job}/executions?page=&size=` | Çalışma geçmişi (yeniden eskiye) |
| GET | `/api/batch/executions/{id}` | Çalışma detayı ve step istatistikleri |
| GET | `/api/batch/executions/{id}/errors` | CSV import'unda reddedilen satırlar |
| POST | `/api/batch/claim-imports` | `multipart/form-data` ile CSV yükle ve içe aktar (en fazla 5 MB) |
| GET | `/api/batch/reports` | Üretilen Excel raporları |
| GET | `/api/batch/reports/{dosya}` | Rapor indir (sadece rapor klasöründeki `.xlsx` dosyaları) |

Aynı job çalışırken yeni bir çalıştırma `JOB_ALREADY_RUNNING` ile reddedilir.

Asenkron çalıştırma için virtual thread kullanan ayrı bir `TaskExecutorJobOperator` vardır. Boot'un senkron
`JobOperator`'ı zamanlayıcı ve testler için olduğu gibi bırakılmıştır.

## Zamanlama

`BatchScheduler`, `@Scheduled` cron ifadeleriyle job'ları tetikler. Lokal demoda kapalıdır:

```yaml
app:
  batch:
    scheduling:
      enabled: ${BATCH_SCHEDULING_ENABLED:false}
      cron:
        overdueInstallmentJob: 0 0 1 * * *
```

Açmak için `.env` dosyasına `BATCH_SCHEDULING_ENABLED=true` ekleyin.

## Performans: Java chunk vs. PL/SQL bulk

### Veri seti

İsteğe bağlı performans verisi bir Flyway migration'ı değildir, elle çalıştırılır:

```bash
docker cp docker/oracle/scripts insurance-oracle:/tmp/
docker exec -it insurance-oracle sqlplus insurance/<şifre>@FREEPDB1 @/tmp/scripts/seed-performance.sql 50000
# tekrar ölçmek için: @/tmp/scripts/perf-reset.sql      silmek için: @/tmp/scripts/perf-cleanup.sql
```

Script, seed araçları üzerinde 50.000 aktif trafik poliçesi ve 200.000 taksit üretir (`FORALL`, 5.000'lik
dilimler, ~35 sn). Başlangıç tarihleri son bir yıla dağıldığı için **174.430 taksitin vadesi geçmiştir** ve
**45.691 poliçe** askıya alınacak duruma gelir.

### Sonuç

Aynı veri üzerinde iki job sırayla çalıştırıldı (arada `perf-reset.sql`). Ortam: Windows 11 dizüstü,
Docker Desktop (WSL2, ~7,8 GB), Oracle Free 23, uygulama aynı makinede.

| | Java chunk (`overdueInstallmentJob`) | PL/SQL bulk (`overdueInstallmentPlsqlJob`) |
|---|---|---|
| Taksit işaretleme (174.430 satır + bildirim) | 41,9 sn, 1.745 commit | — |
| Poliçe askıya alma (45.691 poliçe + bildirim) | 83,9 sn, 457 commit | — |
| **Toplam** | **126,0 sn** | **38,2 sn** (tek çağrı, 220.100 satır yazma) |

PL/SQL sürümü bu veri setinde yaklaşık **3,3 kat** hızlı.

### Neden?

- **Ağ gidiş-dönüşü:** Java tarafında okuma imleci ile yazma batch'leri ayrı round-trip'lerdir. 100'lük chunk'larla
  1.745 transaction'a bölünür. PL/SQL'de veri veritabanından hiç çıkmaz.
- **Askıya alma:** Java step'i her poliçe için `PKG_POLICY.CHANGE_STATUS` çağırır (satır kilitleme + geçiş kontrolü
  + audit trigger), yani 45.691 ayrı çağrı. PL/SQL sürümü aynı güncellemeyi `FORALL` ile 5.000'lik dizilerle tek
  seferde yapar. Askıya alma adımının Java'da iki katı sürmesinin nedeni budur.
- **SQL/PL-SQL bağlam geçişi:** `BULK COLLECT ... LIMIT` ve `FORALL`, satır satır döngüdeki bağlam geçişini
  dilim başına bire indirir.

### Hangisi ne zaman?

| Java chunk job | PL/SQL bulk |
|---|---|
| Her öğe için Java'da iş mantığı veya dış sistem çağrısı (e-posta, REST, dosya) gerekiyorsa | İş tamamen set-based ve veritabanı içindeyse |
| Satır bazında skip/retry, atlanan kayıt raporu, yeniden başlatılabilirlik önemliyse | Hacim büyükse ve süre penceresi darsa (gece batch'i) |
| Spring Batch metadata'sında okunan/yazılan/atlanan istatistik isteniyorsa | İstatistik prosedürden çıktı parametresi olarak alınır |

Bu projede ikisi bir arada kullanılır. Varsayılan zamanlanmış job, izlenebilirlik için chunk-oriented olandır.
Hacim büyüdüğünde aynı iş `overdueInstallmentPlsqlJob` ile yapılabilir. İkisinin aynı sonucu ürettiği
`BatchJobsIntegrationTest` ile doğrulanır.

PL/SQL prosedürü her dilimden sonra `COMMIT` eder (undo küçük kalsın diye). Bu yüzden tasklet Spring
transaction'ı olmadan (`PROPAGATION_NOT_SUPPORTED`) çalışır.

## Testler

`BatchJobsIntegrationTest`, Spring Batch Test'in `JobOperatorTestUtils` sınıfıyla her job'ı Testcontainers Oracle
üzerinde uçtan uca çalıştırır:

- yenileme teklifi, primi ve tekrar çalıştırmada tekrar teklif üretilmemesi,
- taksit gecikmesi ve askıya alma (Java ve PL/SQL sürümleri aynı sonucu verir),
- süre sonu ve yenilenme ayrımı,
- CSV import'unda geçerli/hatalı satırlar, satır numaralı hata raporu ve eksik dosyada job'ın düşmesi,
- Excel raporunun içeriği ve toplamı.

`BatchApiIntegrationTest` REST uçlarını test eder: asenkron çalıştırma ve durum sorgulama, geçersiz parametre,
dosya yükleme, hata listesi, rapor indirme, yetki ve dosya yolu güvenliği.
