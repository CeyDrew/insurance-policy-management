# CLAUDE.md

Portföy projesi: Sigorta Poliçe Yönetim Sistemi (Spring Boot + Angular + Oracle PL/SQL + Spring Batch).
Tüm yol haritası `PLAN.md` içinde. İşe başlamadan önce onu oku ve kaldığın fazı `git log` / branch'lerden tespit et.

## Çalışma kuralları

- Fazları `PLAN.md` sırasıyla uygula. Her faz bitince dur, kullanıcıya kısa özet ver (ne yapıldı, nasıl denenir), onay almadan sonraki faza geçme.
- Her faz için branch: `feature/phase-N-kisa-ad`. Faz bitince testler geçiyorsa `main`'e merge et ve push et.
- Küçük, anlamlı commit'ler. Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`.
- GitHub repo oluşturma, force push, branch silme gibi geri dönüşü zor işlerden önce kullanıcıya sor.
- Sadece lokal çalışır. Deploy adımı, sunucu ayarı ekleme.
- Şifre, token, secret asla commit'lenmez. Değerler `.env` içinde, şablon `.env.example`'da. JWT secret dahil.

## Ortam (Windows 11)

- JDK 22 kurulu; proje `release 21` hedefler. Global Maven yok: her zaman `./mvnw` (Windows'ta `mvnw.cmd`) kullan.
- Node 24, Docker Desktop, `gh` CLI (giriş yapılmış) mevcut.
- Oracle: `docker compose up -d oracle` (`gvenzl/oracle-free`). İlk açılış birkaç dakika sürebilir; healthcheck'i bekle.

## Kod kuralları

- Kod, tablo/kolon adları, commit mesajları İngilizce. UI metinleri Türkçe.
- Backend feature bazlı paketleme (`customer`, `policy`, `premium`...). Controller → Service → Repository. Entity'ler controller'dan dışarı sızmaz, DTO + MapStruct kullanılır.
- Veritabanındaki her değişiklik Flyway migration'ı ile. Yayınlanmış (commit'lenmiş) bir `V__` migration'ını asla düzenleme, yenisini ekle. PL/SQL package/view'lar için `R__` (repeatable) migration kullan.
- İş kuralı ağırlıklı hesaplar (prim, iade, hasarsızlık) PL/SQL'de; Java servis katmanı bunları `SimpleJdbcCall` ile çağırır.
- Her yeni servis/job için test yaz. Veritabanı içeren testler Testcontainers Oracle ile.
- Angular: standalone components, reactive forms, feature klasörleri, servisler `HttpClient` üzerinden typed DTO döner.

## Komutlar

```bash
docker compose up -d oracle          # veritabanı
cd backend && ./mvnw spring-boot:run # backend  (http://localhost:8080, Swagger: /swagger-ui.html)
cd backend && ./mvnw verify          # testler
cd frontend && npm start             # frontend (http://localhost:4200)
docker compose up --build            # hepsi birlikte (Faz 9 sonrası)
```
