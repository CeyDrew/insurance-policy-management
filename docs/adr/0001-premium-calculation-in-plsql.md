# ADR 0001 — Prim, iade ve hasarsızlık hesapları PL/SQL'de

- **Durum:** Kabul edildi
- **Tarih:** 2026-11

## Bağlam

Prim hesabı tarife tablolarına (ürün, yaş, araç değeri, kullanım tipi, hasarsızlık basamağı, bölge...) dayanır.
Aynı hesap üç farklı yerden çağrılır: ekrandaki teklif, gece çalışan yenileme job'ı ve hasar ödemesinden sonra
yenileme tekliflerinin yeniden fiyatlanması. Sigorta sektöründe bu tür kurallar genellikle veritabanında, PL/SQL
paketlerinde yaşar ve aktüerya/BT ekipleri tarifeyi orada bakımını yapar.

Seçenekler:

1. Hesabı Java servis katmanında yazmak (JPA ile tarifeyi okuyup Java'da hesaplamak).
2. Hesabı PL/SQL paketinde yazmak, Java'dan `SimpleJdbcCall` ile çağırmak.

## Karar

Prim (`PKG_PREMIUM`), gün esaslı iade ve poliçe durum geçişleri (`PKG_POLICY`), hasar akışı ve hasarsızlık
basamağı (`PKG_CLAIM`), tahsilat (`PKG_PAYMENT`) PL/SQL'dedir. Java bu paketleri `SimpleJdbcCall` ile çağırır,
PL/SQL'in fırlattığı `ORA-20xxx` hatalarını `PlsqlErrors` ile iş hatalarına çevirir.

## Sonuçlar

- **Artı:** Tarife verisiyle hesap aynı yerde; çok satırlı okumalar ağ turu olmadan yapılır. Batch job, REST API ve
  başka PL/SQL paketleri aynı hesabı tek kaynaktan kullanır. Kurallar veritabanı seviyesinde korunur (doğrudan
  SQL ile gelen değişiklik de aynı geçiş kontrolünden geçer).
- **Artı:** Hata kodları tek formatta (`KOD|Türkçe mesaj`) gelir; Java tarafı sadece çevirir.
- **Eksi:** İş mantığı iki dilde. PL/SQL birim testi yerine Testcontainers Oracle ile entegrasyon testi gerekir
  (`PremiumPackageTest`, `ClaimPackageTest`...); testler daha yavaştır.
- **Eksi:** Hibernate'in birinci seviye cache'i PL/SQL'in yaptığı değişiklikleri görmez. Bunun için
  `DatabaseSideChanges` yardımcı sınıfı PL/SQL çağrısından önce `flush`, gerekirse sonra `clear` yapar.
- Paketler Flyway repeatable migration'larıdır (`R__`); değiştiklerinde bir sonraki açılışta yeniden derlenir.
