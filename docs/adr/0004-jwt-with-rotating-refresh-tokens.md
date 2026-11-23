# ADR 0004 — Kısa ömürlü JWT + dönen refresh token

- **Durum:** Kabul edildi
- **Tarih:** 2026-11

## Bağlam

Angular SPA ile REST API arasında oturum gerekiyor. Rol bazlı yetki (ADMIN, AGENT, CLAIM_HANDLER, CUSTOMER) her
istekte kontrol edilmeli. Sunucu tarafında oturum tutmak (session cookie) yatay ölçeklemeyi ve API'nin başka
istemcilerce kullanılmasını zorlaştırır; uzun ömürlü tek bir token çalındığında uzun süre geçerli kalır.

## Karar

- **Access token:** HS256 imzalı JWT, 15 dakika. Spring Security OAuth2 Resource Server doğrular; rol `roles`
  claim'inden okunur. İmza anahtarı `JWT_SECRET` ortam değişkeninden gelir, repoda yoktur.
- **Refresh token:** Rastgele, 7 gün. Veritabanında sadece SHA-256 hash'i tutulur. Her kullanımda eskisi iptal
  edilip yenisi verilir (rotation); iptal edilmiş bir token tekrar gelirse o kullanıcının tüm refresh
  token'ları iptal edilir (çalınma şüphesi).
- Frontend interceptor 401'de bir kez yenileme dener, eşzamanlı istekler aynı yenilemeyi bekler.

## Sonuçlar

- **Artı:** API stateless; her istek kendi yetkisini taşır. Çalınan access token en fazla 15 dakika işe yarar.
- **Artı:** Çıkış yapınca refresh token iptal edilir; pasifleştirilen kullanıcının token'ı bir sonraki
  yenilemede reddedilir. İkisi de en geç 15 dakikada (access token süresi dolunca) etkili olur.
- **Eksi:** Access token anında iptal edilemez (kara liste tutulmadı). Rol değişikliği bir sonraki yenilemede
  yansır.
- **Eksi:** Token'lar localStorage'da (portföy projesi sadeliği için); XSS'e karşı Angular'ın varsayılan
  escaping'ine güvenilir. Gerçek ortamda refresh token için `HttpOnly` cookie tercih edilmeli.
