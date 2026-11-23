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
| `collectionRequestExportJob` | Vadesi gelen taksitler için bankaya sabit uzunluklu tahsilat talep dosyası yazar | `runDate` | — (ekrandan) |
| `collectionResultImportJob` | Bankanın sonuç dosyasını doğrular, satırları paralel bölümlerle uygular | `fileHash` | — (yükleme, inbox, simülatör) |

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

## Banka tahsilat dosyaları

Otomatik ödeme talimatlı taksitler bankayla sabit uzunluklu dosya alışverişiyle tahsil edilir. Dosya formatı:
[bank-file-format.md](bank-file-format.md). Ekran: **Tahsilat Dosyaları** (ADMIN).

### collectionRequestExportJob (giden dosya)

```
createRequestFileStep    COLLECTION_FILE kaydı (GENERATING), dosya yolu job context'ine
writeRequestFileStep     JdbcCursorItemReader
                           -> CompositeItemWriter
                                FlatFileItemWriter (FormatterLineAggregator, header + footer callback)
                                JdbcBatchItemWriter (installment.collection_request_file_id)
completeRequestFileStep  kayıt sayısı, toplam tutar, SHA-256, GENERATED
```

- **Seçim:** `ACTIVE`/`SUSPENDED` poliçelerin `PENDING`/`OVERDUE` taksitleri, vadesi `runDate + days-ahead (7)`
  gününe kadar olanlar. Cevabı beklenen bir taksit (başka bir talep dosyasına bağlı) tekrar istenmez. Reddedilen
  taksit bir sonraki dosyaya tekrar girer.
- **Header/trailer:** Header callback dosya numarasını ve tarihi yazar. Trailer, step kapanırken, son chunk commit
  edildikten sonra veritabanından okunan sayı ve toplamla yazılır. Bu sayede restart sonrasında da doğrudur.
- **Restart güvenliği:** Reader sorgusu bu dosyaya zaten bağlanmış taksitleri de getirir (`... OR
  collection_request_file_id = :fileId`). Yeniden başlatılan step, kaldığı yere sayarak atladığında tam olarak
  yazılmış satırları atlar. `FlatFileItemWriter` dosyayı son commit edilen konuma kırpar ve oradan devam eder.

### collectionResultImportJob (gelen dosya)

```
validateResultFileStep   dosyanın tamamı: header, sıra numaraları, trailer sayısı + toplam tutar
applyResultsStep         Partitioner: detay satırlarını N aralığa böler (varsayılan 4, en çok 8)
  applyResultsWorkerStep   FlatFileItemReader (PatternMatchingCompositeLineMapper + FixedLengthTokenizer)
                           -> PKG_COLLECTION.APPLY_RESULTS (chunk başına tek çağrı, JSON)
completeResultFileStep   sayaçlar COLLECTION_RESULT'tan, COMPLETED
```

- **Trailer kontrolü:** Kayıt sayısı veya toplam tutar tutmazsa ilk step hata verir. Dosya `FAILED` olarak kaydedilir
  (ayrı transaction'da, step rollback'inden etkilenmez), job `FAILED` olur ve **hiçbir satır işlenmez**.
- **Mükerrer dosya:** Tek identifying job parametresi dosyanın SHA-256'sıdır. Aynı içerik aynı job instance'ı
  demektir: tamamlanmış bir dosya ikinci kez başlatılamaz (`JobInstanceAlreadyCompleteException`). Servis bunu
  job'ı başlatmadan önce `DUPLICATE_FILE` ile reddeder. Veritabanında da fonksiyon tabanlı unique index vardır.
- **Restart:** Uygulama sırasında kesilen dosya `PROCESSING` kalır ve sebebi ekranda görünür. "Yeniden başlat"
  aynı execution'ı `JobOperator.restart` ile devam ettirir:
  - Doğrulama step'i tekrar çalışmaz.
  - Tamamlanmış partition'lar atlanır.
  - Kesilen partition son commit edilen chunk'tan devam eder.
  - PL/SQL aynı satırı ikinci kez uygulamaz.
  - Demo ve test için `app.batch.collection.fail-at-line` (veya testte `CollectionFaultInjector`) belirli bir
    satırda yapay hata üretir.
- **Partitioning, multi-threaded step değil:** Çok thread'li tek bir step'te `FlatFileItemReader` paylaşılır,
  thread-safe değildir ve okuma konumu kaydedilemez, yani restart edilemez. Partition'larda her worker kendi
  satır aralığını kendi reader'ıyla okur ve kendi konumunu kaydeder. Paralellik ve restart birlikte elde edilir.
  Aynı poliçenin satırları talep dosyasında art arda yazıldığından çoğunlukla aynı partition'a düşer. Yine de
  iki partition aynı poliçeyi kilitlerse (ORA-00060) chunk yeniden denenir.
- **Dosya izleme:** `BATCH_COLLECTION_POLL_INBOX=true` iken `CollectionInboxPoller`, `<collection dir>/inbox/`
  klasörünü tarar. Son 3 saniyede değişmemiş her dosyayı `inbound/`'a kopyalayıp senkron olarak içe aktarır.
  Orijinal dosya, sonuca göre `processed/` ya da `error/` klasörüne taşınır. Docker Compose'da açıktır.

### API

| Metot | Yol | Açıklama |
|---|---|---|
| GET | `/api/collection-files?direction=&page=&size=` | Talep ve sonuç dosyaları |
| GET | `/api/collection-files/{id}` | Dosya; sonuç dosyası için red sebepleri, talep için cevap dosyaları |
| GET | `/api/collection-files/{id}/results?outcome=` | Uygulanan satırlar (tahsil / red / hatalı) |
| GET | `/api/collection-files/{id}/download` | Dosyayı indir |
| POST | `/api/collection-files/requests` | Talep dosyası üret |
| POST | `/api/collection-files/results` | Sonuç dosyası yükle (`multipart/form-data`, en fazla 10 MB) |
| POST | `/api/collection-files/{id}/bank-answer?partitions=` | Demo: banka simülatörü talebe cevap üretir ve işler |
| POST | `/api/collection-files/{id}/restart` | Yarıda kalmış içe aktarmayı devam ettir |

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

## Performans: 1 milyon satırlık banka sonuç dosyası

### Veri seti

`seed-performance.sql 300000` ile 300.000 poliçe ve 1.200.000 taksit üretildi. Talep dosyası vadesi gelen
**1.068.134** taksiti içerdi. Banka simülatörünün cevabında 939.878 satır tahsil, 128.256 satır red
(`51` 62.345, `54` 32.472, `57` 21.439, `91` 12.000), 0 hatalı satır vardı. İki ölçüm arasında
`collection-perf-reset.sql` ile veri ilk haline döndürüldü. İkinci talep dosyası da aynı 1.068.134 satırı içerdi.

```bash
docker exec -it insurance-oracle sqlplus insurance/<şifre>@FREEPDB1 @/tmp/scripts/seed-performance.sql 300000
# ekrandan: Talep dosyası üret -> Banka cevabı   (veya POST /api/collection-files/{id}/bank-answer?partitions=N)
docker exec -it insurance-oracle sqlplus insurance/<şifre>@FREEPDB1 @/tmp/scripts/collection-perf-reset.sql
```

### Sonuç

Ortam: Windows 11 dizüstü, Docker Desktop (WSL2, ~7,8 GB), Oracle Database Free 23, chunk 100.

| Adım | Süre | Hız |
|---|---|---|
| Talep dosyası (`collectionRequestExportJob`, 1.068.134 satır, 10.682 commit) | 143 sn / 152 sn | ~7.200 satır/sn |
| Sonuç dosyası doğrulama (dosyanın tamamı, trailer kontrolü) | 8,6 sn | ~124.000 satır/sn |
| Sonuç uygulama, **1 partition** | **837,7 sn** (tamamı) | **~1.275 satır/sn** |
| Sonuç uygulama, **4 partition** | 708 sn'de 705.800 satır (ölçüm burada durduruldu) | **~997 satır/sn** |

4 partition bu ortamda tek thread'den **yaklaşık %22 yavaş** çıktı. Ölçüm tamamlanmadı, ama dört partition da
eşit hızda (her biri ~175 bin satır) ilerliyordu, yani hız oranı sabitti.

### Neden paralel daha hızlı değil?

- **Darboğaz veritabanında.** Her satır PL/SQL'de birkaç kilit ve yazma demek: taksit ve poliçe kilidi, ödeme,
  bildirim, sonuç satırı. Java tarafı (dosya okuma, JSON) toplam sürenin çok küçük bir kısmı.
- **Oracle Database Free en fazla 2 CPU thread kullanır.** Dört worker bu iki thread için yarışır, araya bağlam
  değişimi ve aynı index bloklarına eşzamanlı ekleme (sequence ile artan `PAYMENT`, `NOTIFICATION`,
  `COLLECTION_RESULT` birincil anahtarları, sağ uç blok çekişmesi) girer.
- Lisanslı bir Oracle'da (daha çok çekirdek, gerekirse sequence `CACHE`/scalable sequence, reverse-key veya hash
  partitioned index) partition sayısı artırılarak ölçeklenir. Ayar bu yüzden kodda sabit değil:
  `app.batch.collection.partitions` ve istek başına `partitions` parametresi.

Bu ortam için doğru ayar 1 veya 2 partition'dır. Partitioning'in bu projedeki asıl kazancı, aralıkların
birbirinden bağımsız commit edilmesi ve yeniden başlatılabilmesidir.

### Ölçüm sırasında bulunan sorun

`collection-perf-reset.sql` ilk denemede 25 dakikada bitmedi. `COLLECTION_RESULT.PAYMENT_ID` foreign key'inde
index yoktu: silinen her `PAYMENT` satırı için Oracle `COLLECTION_RESULT`'ı baştan sona taradı. `V14` ile index
eklendikten sonra 1,2 milyon bildirim, 1,2 milyon sonuç ve 940 bin ödeme satırının silinmesi ve 1,2 milyon
taksitin güncellenmesi **438 saniye** sürdü. Kural: referans veren her foreign key kolonu indexlenir.

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

Banka tahsilat dosyaları için:

- `BankFileTest`: alanların belgedeki konumlarda yazılması ve geri okunması, yanlış uzunlukta satırın reddi,
  simülatör ve partition aralıkları.
- `CollectionJobsIntegrationTest`: talep dosyasının kontrol toplamları; tahsil, red ve hatalı satırlar; trailer
  hatasında hiçbir satırın işlenmemesi; aynı dosyanın ikinci kez uygulanamaması; 130. satırda yapay çökme sonrası
  restart'ta tamamlanan partition'ın atlanması ve her taksitin tek kez ödenmesi.
- `CollectionFileApiIntegrationTest`: ekranın kullandığı uçlar, simülatörle uçtan uca akış, mükerrer yükleme ve
  yetki. `CollectionInboxPollerTest`: inbox klasöründen `processed/` ve `error/` klasörlerine taşıma.
