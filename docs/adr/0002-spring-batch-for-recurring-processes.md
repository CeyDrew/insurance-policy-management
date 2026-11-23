# ADR 0002 — Periyodik süreçler için Spring Batch

- **Durum:** Kabul edildi
- **Tarih:** 2026-11

## Bağlam

Her gece çalışması gereken işler var: vadesi geçen taksitleri işaretlemek ve poliçeyi askıya almak, süresi dolan
poliçeleri kapatmak, yenileme teklifi üretmek. Bunlara ek olarak toplu hasar ihbarı (CSV) ve aylık Excel üretim
raporu var. Veri hacmi büyüyebilir (yüz binlerce taksit), işin yarıda kesilmesi ve tekrar çalıştırılması
durumunda tutarlı kalmalı, hangi satırın neden atlandığı görülebilmeli.

Seçenekler:

1. `@Scheduled` metodlar + elle yazılmış döngüler.
2. Spring Batch job'ları (chunk-oriented step, job repository).
3. Tamamen PL/SQL (`DBMS_SCHEDULER` job'ları).

## Karar

Spring Batch 6. Okuma `JdbcCursorItemReader`, yazma JDBC batch ile; chunk başına tek transaction. Geçici hatalarda
retry, hatalı satırda skip ve skip edilen satırların kaydı. Job metadata'sı Oracle'da (`BATCH_*` tabloları
Flyway ile). Zamanlama `@Scheduled` + cron ile, ayrıca yönetim panelinden elle başlatma ve izleme.

Gecikme işaretleme için bir de PL/SQL karşılığı var (`overdueInstallmentPlsqlJob`, `BULK COLLECT`/`FORALL`);
iki yaklaşım aynı veriyle ölçülüp `docs/batch-jobs.md`'de karşılaştırıldı.

## Sonuçlar

- **Artı:** Her çalıştırmanın okunan/yazılan/atlanan sayıları, süresi ve hatası `BATCH_STEP_EXECUTION`'da;
  yönetim paneli bunları gösterir. Başarısız job aynı parametrelerle yeniden başlatılabilir.
- **Artı:** İş kuralı (prim) yine PL/SQL'de kalır; processor sadece çağırır. Job'lar idempotent yazıldığı için
  aynı gün iki kez çalışmak sorun çıkarmaz.
- **Eksi:** Ek tablo seti ve kavram yükü (job, step, chunk, `@StepScope`). Küçük işler için ağır.
- **Eksi:** Çok büyük set-based güncellemelerde PL/SQL daha hızlı; bu yüzden ikisi yan yana tutuldu.
