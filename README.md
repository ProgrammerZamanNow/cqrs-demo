# Product Backend

Backend service untuk **product management**. Menyediakan REST API untuk mengelola
**produk**, **kategori**, dan **brand**, lengkap dengan manajemen stok dan pencarian
produk ala toko online (filter, sorting, pagination, dan **facet** dengan jumlah
produk per item).

Spesifikasi lengkap ada di [`../REQUIREMENT.md`](../REQUIREMENT.md).

---

## Daftar Isi
- [Fitur](#fitur)
- [Tech Stack](#tech-stack)
- [Prasyarat](#prasyarat)
- [Menjalankan Aplikasi](#menjalankan-aplikasi)
- [Menjalankan Test](#menjalankan-test)
- [Ringkasan API](#ringkasan-api)
- [Format Respons](#format-respons)
- [Struktur Project](#struktur-project)
- [Konvensi & Keputusan Desain](#konvensi--keputusan-desain)

---

## Fitur

- **CRUD Kategori** — buat, lihat, ubah, hapus (ditolak bila masih dipakai produk).
- **CRUD Brand** — sama seperti kategori.
- **CRUD Produk** — termasuk relasi ke kategori & brand, harga, stok, dan URL gambar.
- **Update stok** — menambah/mengurangi stok dengan row lock (`SELECT ... FOR UPDATE`),
  stok tidak boleh negatif.
- **Search produk** — keyword (nama & deskripsi), filter kategori (multi), brand
  (multi), rentang harga, ketersediaan stok; sorting & pagination.
- **Facet** — kategori, brand, rentang harga, dan ketersediaan beserta jumlah produk
  per item, dengan perilaku **drill-down** (memilih satu nilai tidak menghilangkan
  pilihan lain pada dimensi yang sama).
- **Metadata waktu proses** — setiap respons membawa `metadata.processTimeMs` dan
  header `X-Process-Time-Ms`.

---

## Tech Stack

| Komponen        | Pilihan                                  |
|-----------------|------------------------------------------|
| Bahasa          | Java 25                                  |
| Framework       | Spring Boot 4.0.x                         |
| Database        | PostgreSQL                               |
| Akses Data      | Spring Data JPA / Hibernate              |
| Migrasi DB      | Flyway                                   |
| Build Tool      | Maven                                    |
| Dokumentasi API | springdoc OpenAPI (Swagger UI)           |
| Testing         | JUnit 5, Spring Test (MockMvc), Testcontainers |

> Catatan: Spring Boot 4 memakai **Jackson 3** (`tools.jackson`) sebagai default dan
> struktur auto-configuration yang modular (mis. `spring-boot-flyway`,
> `spring-boot-starter-webmvc-test`).

---

## Prasyarat

- **JDK 25** (proyek diuji dengan GraalVM CE 25).
- **Maven 3.9+** (atau gunakan `./mvnw` bila wrapper tersedia).
- **PostgreSQL** untuk menjalankan aplikasi (tidak diperlukan untuk test).
- **Podman** untuk menjalankan integration test (Testcontainers). Lihat
  [Menjalankan Test](#menjalankan-test).

Cek versi:
```bash
java -version      # harus 25
mvn -version
```

---

## Menjalankan Aplikasi

### 1. Siapkan PostgreSQL

Buat database, mis. `product`. Contoh cepat memakai Podman:
```bash
podman run -d --name product-postgres \
  -e POSTGRES_DB=product \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  docker.io/library/postgres:17-alpine
```

### 2. Konfigurasi (via environment variable)

| Variable      | Default                                   | Keterangan          |
|---------------|-------------------------------------------|---------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/product`| JDBC URL PostgreSQL |
| `DB_USERNAME` | `postgres`                                | Username DB         |
| `DB_PASSWORD` | `postgres`                                | Password DB         |
| `SERVER_PORT` | `8080`                                    | Port HTTP           |

Schema database dibuat otomatis oleh **Flyway** saat aplikasi start
(`src/main/resources/db/migration`).

### 3. Jalankan

```bash
# mode development
mvn spring-boot:run

# atau build jar lalu jalankan
mvn clean package
java -jar target/product-backend-0.0.1-SNAPSHOT.jar
```

### 4. Akses

- API base path: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health check: `http://localhost:8080/actuator/health`

Contoh request:
```bash
# buat kategori
curl -X POST http://localhost:8080/api/categories \
  -H 'Content-Type: application/json' \
  -d '{"name":"Electronics","description":"Electronic devices"}'

# cari produk
curl 'http://localhost:8080/api/products?keyword=mouse&page=0&size=10&sort=price,asc'
```

---

## Menjalankan Test

Integration test memakai **Testcontainers** yang otomatis menjalankan container
PostgreSQL. Lingkungan dev di proyek ini memakai **Podman** (bukan Docker).

### 1. Pastikan Podman hidup

```bash
podman machine start
```

### 2. Arahkan Testcontainers ke Podman & nonaktifkan Ryuk

Pada macOS, `podman machine start` umumnya sudah meneruskan socket ke
`/var/run/docker.sock`, sehingga `DOCKER_HOST` tidak wajib diset. Ryuk (resource
reaper) perlu dinonaktifkan karena sering bermasalah di Podman rootless:

```bash
export TESTCONTAINERS_RYUK_DISABLED=true
```

Bila socket default tidak terdeteksi, set juga:
```bash
export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
```

Alternatif permanen lewat `~/.testcontainers.properties`:
```properties
ryuk.disabled=true
docker.host=unix:///<path-socket-podman>
```

### 3. Jalankan test

```bash
TESTCONTAINERS_RYUK_DISABLED=true mvn test
```

Test mencakup seluruh endpoint: CRUD kategori, brand, dan produk, update stok,
serta search + facet (termasuk skenario drill-down).

> Catatan: bila proses test mati paksa saat Ryuk dinonaktifkan, container sisa perlu
> dibersihkan manual: `podman ps -a` lalu `podman rm <id>`.

---

## Ringkasan API

Base path: `/api`. Detail request/response ada di [`../REQUIREMENT.md`](../REQUIREMENT.md).

### Kategori
| Method | Path                  | Keterangan             |
|--------|-----------------------|------------------------|
| POST   | `/api/categories`     | Buat kategori          |
| GET    | `/api/categories`     | List kategori (paging) |
| GET    | `/api/categories/{id}`| Detail kategori        |
| PUT    | `/api/categories/{id}`| Update kategori        |
| DELETE | `/api/categories/{id}`| Hapus kategori         |

### Brand
| Method | Path              | Keterangan         |
|--------|-------------------|--------------------|
| POST   | `/api/brands`     | Buat brand         |
| GET    | `/api/brands`     | List brand (paging)|
| GET    | `/api/brands/{id}`| Detail brand       |
| PUT    | `/api/brands/{id}`| Update brand       |
| DELETE | `/api/brands/{id}`| Hapus brand        |

### Produk
| Method | Path                      | Keterangan                          |
|--------|---------------------------|-------------------------------------|
| POST   | `/api/products`           | Buat produk                         |
| GET    | `/api/products`           | Search produk (filter, sort, facet) |
| GET    | `/api/products/{id}`      | Detail produk                       |
| PUT    | `/api/products/{id}`      | Update produk                       |
| PATCH  | `/api/products/{id}/stock`| Update stok (INCREASE/DECREASE)     |
| DELETE | `/api/products/{id}`      | Hapus produk (hard delete)          |

**Query parameter search** (`GET /api/products`): `keyword`, `categoryId` (boleh
diulang), `brandId` (boleh diulang), `minPrice`, `maxPrice`, `availability`
(`IN_STOCK`/`OUT_OF_STOCK`), `page`, `size` (maks 100), `sort` (`field,asc|desc`;
field: `name`, `price`, `stock`, `createdAt`, `updatedAt`).

---

## Format Respons

Semua respons dibungkus envelope standar.

Sukses (object):
```json
{ "data": { }, "metadata": { "processTimeMs": 12 } }
```

Sukses (list):
```json
{ "data": [ ], "paging": { "page": 0, "size": 10, "totalElement": 135, "totalPage": 14 },
  "metadata": { "processTimeMs": 27 } }
```

Search (dengan facet):
```json
{ "data": [ ], "paging": { }, "facets": { "categories": [], "brands": [],
  "priceRanges": [], "availability": [] }, "metadata": { "processTimeMs": 35 } }
```

Error:
```json
{ "error": "price must be greater than or equal to 0", "metadata": { "processTimeMs": 5 } }
```

HTTP status: `200` (GET/PUT/PATCH/DELETE), `201` (POST), `400` (validasi),
`404` (tidak ditemukan), `409` (konflik), `500` (error server).

---

## Struktur Project

```
product-backend/
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/pzn/product
    │   │   ├── ProductBackendApplication.java
    │   │   ├── web/         # envelope, metadata/process-time, exception handler, pageable
    │   │   ├── exception/   # NotFound / Conflict / BadRequest
    │   │   ├── category/    # entity, repository, service, controller, dto
    │   │   ├── brand/       # entity, repository, service, controller, dto
    │   │   └── product/     # entity, repository, service, controller, dto
    │   │       └── search/  # filter, predicates, facets, search service
    │   └── resources
    │       ├── application.yml
    │       └── db/migration/V1__init_schema.sql
    └── test/java/com/pzn/product
        ├── TestcontainersConfiguration.java
        ├── AbstractIntegrationTest.java
        ├── category/CategoryApiTest.java
        ├── brand/BrandApiTest.java
        └── product/ProductApiTest.java, ProductSearchApiTest.java
```

---

## Konvensi & Keputusan Desain

- **Primary key** memakai **UUID** (di-generate aplikasi).
- **Timestamp** (`createdAt`, `updatedAt`) berupa **Long epoch millis** (UTC).
  Frontend yang menampilkan sesuai timezone.
- **Mata uang** semua `price` dalam **IDR** (tanpa kode mata uang di payload).
- **Tanpa autentikasi** — di luar lingkup service ini.
- **Delete produk** bersifat **hard delete**.
- **Gambar produk** disimpan sebagai URL; bila kosong dipakai URL dummy default.
- **Update stok** memakai row lock (`SELECT ... FOR UPDATE`) agar aman dari race
  condition.
- **Content-Type** selalu `application/json` (UTF-8).
