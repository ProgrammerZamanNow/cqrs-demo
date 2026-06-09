# product-search

**Read model CQRS**: mengonsumsi perubahan dari PostgreSQL (via Debezium/Kafka),
memproyeksikannya ke **OpenSearch**, dan menyajikan API pencarian
`GET /api/products/_search`.

Bagian dari [CQRS Demo](../README.md) — untuk menjalankan full stack, ikuti README root.

---

## Peran

```
Kafka (topik Debezium) ──consume──▶ product-search ──index──▶ OpenSearch
   pzn.public.products                (projector +
   pzn.public.brands                   /_search API)
   pzn.public.categories
```

- **Projector (CDC consumer)**: 3 Kafka listener (batch).
  - `categories` & `brands` → cache in-memory `id → nama` ([`RefCache`](src/main/java/com/pzn/search/index/RefCache.java)).
  - `products` → bulk index/delete dokumen ke OpenSearch.
- **Search API**: query OpenSearch dengan facet drill-down, mengembalikan envelope
  **identik** dengan `product-backend` (`data`/`paging`/`facets`/`metadata`).

---

## Tech Stack
- Java 25, Spring Boot 4, Maven
- Spring Kafka (`spring-boot-kafka`) — consumer CDC
- OpenSearch via HTTP (java.net.http) + Jackson — index & query
- Tanpa database sendiri; state hanya di OpenSearch + cache in-memory.

---

## Detail Pencarian

### n-gram (mirip `LIKE`)
Field `name` & `description` di-index dengan analyzer **n-gram** (`min_gram=2`,
`max_gram=3`). Query keyword memakai **`match_phrase`** sehingga n-gram harus
berurutan → cocok **substring panjang berapa pun** (mirip `LIKE '%kata%'`), tetapi
terindeks → cepat. Keyword dicari di `name` **atau** `description`.

### Facet drill-down
Satu request menghasilkan hits + semua facet. Tiap facet dihitung dengan semua filter
aktif **kecuali** filter dimensinya sendiri (`filter` aggregation per dimensi),
sementara `post_filter` menyaring hits. Pola standar faceted-search OpenSearch.

### Denormalisasi
Dokumen produk menyimpan `categoryId`/`brandId` saja; nama di-resolve saat baca dari
`RefCache`. Akibatnya **rename** brand/kategori langsung tercermin tanpa reindex.

---

## Konfigurasi (environment variable)
| Variable          | Default                  | Keterangan                  |
|-------------------|--------------------------|-----------------------------|
| `SERVER_PORT`     | `8081`                   | Port HTTP                   |
| `KAFKA_BOOTSTRAP` | `localhost:9092`         | Bootstrap Kafka             |
| `OPENSEARCH_URI`  | `http://localhost:9200`  | URL OpenSearch              |

Index `products` (beserta mapping n-gram) dibuat otomatis saat startup bila belum ada.

---

## Menjalankan

Disarankan via compose dari root (`podman compose up -d --build`). Untuk dev lokal
(infra Kafka/OpenSearch sudah jalan via compose):

```bash
KAFKA_BOOTSTRAP=localhost:9092 OPENSEARCH_URI=http://localhost:9200 \
  ./mvnw spring-boot:run
```

Contoh query:
```bash
curl 'http://localhost:8081/api/products/_search?keyword=mouse&page=0&size=12&sort=price,asc'
```

Parameter sama persis dengan `GET /api/products` di product-backend: `keyword`,
`categoryId` (multi), `brandId` (multi), `minPrice`, `maxPrice`, `availability`
(`IN_STOCK`/`OUT_OF_STOCK`), `page`, `size`, `sort`.
