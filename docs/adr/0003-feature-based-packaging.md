# ADR 0003 — Feature bazlı paketleme

- **Durum:** Kabul edildi
- **Tarih:** 2026-11

## Bağlam

Klasik katman paketlemesi (`controller`, `service`, `repository`, `entity`) proje büyüdükçe her değişikliği dört
pakete dağıtır ve hangi sınıfların birbirine ait olduğunu gizler. Projede birbirinden oldukça bağımsız iş
alanları var: müşteri, varlık (araç/konut), poliçe, prim, hasar, tahsilat, batch, raporlama, kullanıcı.

## Karar

Backend iş alanına göre paketlenir: `customer`, `asset`, `policy`, `premium`, `claim`, `payment`, `batch`,
`report`, `user`, `security`; ortak altyapı `common` ve `config` altında. Her paketin içinde
Controller → Service → Repository akışı vardır. Entity'ler paket dışına sızmaz: controller'lar DTO döner,
dönüşüm MapStruct ile yapılır. Paket içinde kalabilecek sınıflar package-private'tır.

Frontend aynı ayrımı izler: `features/customers`, `features/policies`, `features/claims`... Her feature kendi
modelleri, servisi ve standalone component'leriyle birlikte; ortak parçalar `shared/` altında.

## Sonuçlar

- **Artı:** Bir özellik üzerinde çalışırken dosyalar tek klasörde. Package-private görünürlük, başka feature'ın
  repository'yi doğrudan kullanmasını derleyici seviyesinde engeller.
- **Artı:** Feature'lar ileride ayrı modüle bölünmeye hazır.
- **Eksi:** Paketler arası gerçek bağımlılıklar (poliçe ↔ prim ↔ hasar) yine var; bunlar public servis
  metodları üzerinden geçer, entity paylaşımı yerine id ile referans tercih edilir.
