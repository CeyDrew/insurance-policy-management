# Sigorta Poliçe Yönetim Sistemi

Sigorta sektörüne yönelik, uçtan uca bir poliçe yönetim sistemi. Portföy projesidir; yalnızca lokal ortamda çalışır.

> **EN summary:** Insurance policy management system built with Spring Boot, Angular, Oracle PL/SQL and
> Spring Batch. Premium calculation lives in PL/SQL packages, recurring business processes run as
> Spring Batch jobs, and an Angular Material admin panel drives the whole flow. Local-only portfolio project.

## Teknolojiler

| Katman | Teknoloji |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Spring Security (JWT), Spring Batch, Flyway |
| Veritabanı | Oracle Database Free 23 (Docker), PL/SQL package / trigger / view |
| Frontend | Angular 22, Angular Material, standalone components |
| Test | JUnit 5, Mockito, Testcontainers (Oracle), Vitest |

Yol haritası ve faz planı: [PLAN.md](PLAN.md)

## Gereksinimler

- JDK 21+ (`JAVA_HOME` 21 veya üstünü göstermeli)
- Node.js 22+ ve npm
- Docker Desktop

Maven kurulu olması gerekmez; proje Maven Wrapper (`mvnw`) ile gelir.

## Nasıl çalıştırılır

```bash
# 1. Ortam değişkenleri (şifreleri değiştir)
cp .env.example .env

# 2. Oracle veritabanı (ilk açılış birkaç dakika sürebilir)
docker compose up -d --wait oracle

# 3. Backend  ->  http://localhost:8080/actuator/health
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# 4. Frontend ->  http://localhost:4200
cd frontend
npm install
npm start
```

Backend, `.env` dosyasını kök dizinden otomatik okur. Veritabanı şeması Flyway migration'ları ile uygulama
açılışında oluşturulur.

| Adres | Açıklama |
|---|---|
| http://localhost:4200 | Yönetim paneli |
| http://localhost:8080/swagger-ui.html | API dokümantasyonu (token, Authorize butonuyla girilir) |
| http://localhost:8080/actuator/health | Sağlık kontrolü |

### Demo kullanıcılar

Hepsinin şifresi `Demo123!`

| Kullanıcı | Rol |
|---|---|
| `admin` | Yönetici |
| `agent`, `agent2` | Acente |
| `claims` | Hasar uzmanı |

Kimlik doğrulama iki token ile çalışır: kısa ömürlü JWT access token (15 dk) ve her kullanımda yenilenen
refresh token (7 gün, veritabanında hash'lenmiş). API hataları RFC 7807 `ProblemDetail` formatında döner.

## Testler

```bash
cd backend && ./mvnw verify      # Testcontainers ile geçici bir Oracle konteyneri açar (Docker gerekli)
cd frontend && npm test -- --watch=false
```

## Proje yapısı

```
backend/    Spring Boot uygulaması (feature bazlı paketler, Flyway migration'ları)
frontend/   Angular yönetim paneli
docker/     Oracle init script'leri
docs/       Mimari, ER diyagramı, PL/SQL ve batch dokümantasyonu, ADR'ler
```
