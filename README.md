# CQRS Demo — Product Search

Demo **CQRS** untuk pencarian produk: sisi **write** memakai PostgreSQL, sisi
**read** (search + facet) memakai **OpenSearch**, disinkronkan via **CDC (Debezium)**
melalui **Kafka**. Frontend bisa **switch** antara PostgreSQL dan OpenSearch untuk
membandingkan performa pencarian secara langsung.

> Tujuan demo: menunjukkan bahwa keyword search `LIKE '%...%'` + facet di PostgreSQL
> menjadi lambat pada data besar (~detik di 1 juta row), sementara read model
> OpenSearch melayani query yang sama dalam puluhan milidetik.

---

## Arsitektur

```
                 WRITE (command)                              READ (query)
 ┌──────────────┐  JDBC   ┌────────────┐  CDC/Debezium  ┌──────────┐  ┌──────────────┐  ┌────────────┐
 │product-backend│ ──────▶ │ PostgreSQL │ ──(WAL)──▶ Connect ──▶ │  Kafka   │─▶│product-search│─▶│ OpenSearch │
 │ /api/products │         │ (sumber    │            (Debezium)  │ (topik)  │  │ consumer +   │  │ (read model)│
 │  (PostgreSQL) │         │  kebenaran)│                        │          │  │ /_search API │  │            │
 └──────────────┘         └────────────┘                        └──────────┘  └──────────────┘  └────────────┘
        ▲                                                                              ▲
        │                                  ┌───────────────────────────────────────────┘
        │      nginx route /api ───────────┤
 ┌──────┴───────┐  /api/products/_search ──┘
 │product-frontend│  (toggle PostgreSQL ⇄ OpenSearch)
 │   (nginx SPA) │
 └──────────────┘
```

| Komponen          | Peran                                              | Port host |
|-------------------|----------------------------------------------------|-----------|
| `product-backend` | Write model + CRUD + search PostgreSQL             | 8080      |
| `product-search`  | Read model: CDC consumer + search OpenSearch       | 8081      |
| `product-frontend`| SPA (SvelteKit) disajikan nginx + routing API      | 3000      |
| `postgres`        | Source of truth (`wal_level=logical`)              | 5433      |
| `kafka`           | Broker CDC (KRaft)                                 | 9092      |
| `connect`         | Debezium (Kafka Connect)                           | 8083      |
| `opensearch`      | Read store (n-gram analyzer)                       | 9200      |
| `product-faker`   | Generator data (Bun) — **bukan** container         | -         |

---

## Prasyarat

- **Podman** (atau Docker) + `podman compose`. VM Podman disarankan **≥ 8 GB RAM**
  (`podman machine set --memory 8192 --cpus 4`) karena ada Kafka + OpenSearch + 2 JVM.
- **Bun** (untuk menjalankan `product-faker`).
- `make`, `curl`, `python3` (untuk perintah bantu — opsional).

Tiap project app (backend/search) memakai Java 25 + Maven, **tetapi tidak perlu
dipasang di host** karena build terjadi di dalam image (Docker multi-stage).

---

## Menjalankan Full Demo

### 1. Naikkan seluruh stack
```bash
cd cqrs-demo
podman compose up -d --build      # atau: docker compose up -d --build
```
Tunggu hingga semua sehat (cek `podman ps`). `product-backend` akan menjalankan
migrasi Flyway yang membuat tabel **dan** `PUBLICATION dbz_publication` (untuk CDC).

### 2. Daftarkan Debezium connector
Jalankan **setelah** `product-backend` selesai migrasi (publication sudah ada):
```bash
make register          # POST debezium/products-connector.json ke connect:8083
make status            # pastikan connector & task RUNNING
```

### 3. Generate data
```bash
cd product-faker
bun install
bun run index.ts                       # default 100 brand, 50 kategori, 100K produk
# untuk demo "lambat", pakai 1 juta:
PRODUCT_COUNT=1000000 bun run index.ts
```
Data ditulis ke `product-backend` (:8080) → PostgreSQL → **mengalir otomatis** via
CDC ke OpenSearch. Pantau: `make os-count`.

### 4. Buka frontend
```
http://localhost:3000
```
Pakai **combo box di kiri search bar** untuk memilih **PostgreSQL** atau
**OpenSearch**, ketik keyword, dan bandingkan badge `processTimeMs` (PostgreSQL akan
menampilkan ⚠ lambat saat > 1 detik pada data besar).

---

## Perintah Bantu (Makefile)

| Perintah          | Fungsi                                            |
|-------------------|---------------------------------------------------|
| `make up`         | `compose up -d`                                   |
| `make build`      | `compose up -d --build`                           |
| `make down`       | Stop semua container                              |
| `make clean`      | Stop + hapus volume (reset total data)            |
| `make register`   | Daftarkan Debezium connector                      |
| `make status`     | Status connector & task                           |
| `make topics`     | List topik Kafka                                  |
| `make os-count`   | Jumlah dokumen di index OpenSearch `products`     |
| `make psql`       | Buka psql ke PostgreSQL                            |
| `make logs`       | Tail log semua service                            |

---

## Verifikasi Cepat

```bash
# parity PostgreSQL vs OpenSearch (total harus sama, OS lebih cepat)
curl -s 'http://localhost:8080/api/products?keyword=rubber&size=1'          # PostgreSQL
curl -s 'http://localhost:8081/api/products/_search?keyword=rubber&size=1'  # OpenSearch
```
Kedua endpoint memakai **envelope identik** (`data`/`paging`/`facets`/`metadata`),
jadi frontend tinggal menukar URL.

---

## Endpoint per Service

- **product-backend** (`:8080`) — CRUD + search PostgreSQL. Swagger:
  `http://localhost:8080/swagger-ui.html`. Lihat [product-backend/README.md](product-backend/README.md).
- **product-search** (`:8081`) — `GET /api/products/_search` (OpenSearch).
  Lihat [product-search/README.md](product-search/README.md).
- **product-frontend** (`:3000`) — SPA. Lihat [product-frontend/README.md](product-frontend/README.md).
- **product-faker** — generator data. Lihat [product-faker/README.md](product-faker/README.md).

Spesifikasi REST lengkap (request/response) ada di [REQUIREMENT.md](REQUIREMENT.md).

---

## Troubleshooting

- **Connector tidak RUNNING** (`make status`): pastikan `product-backend` sudah
  migrasi (publication `dbz_publication` ada). Cek `make logs` service `connect`.
  Daftar ulang: `make unregister && make register`.
- **OpenSearch tetap 0** (`make os-count`): cek topik ada isinya (`make topics`),
  dan log `product-search` menampilkan `partitions assigned`. Pastikan connector RUNNING.
- **`wal_level` bukan `logical`**: postgres compose sudah di-set lewat `command`.
  Bila pakai postgres lain, set `wal_level=logical` lalu restart.
- **Container OOM / lambat**: naikkan RAM VM Podman ke 8 GB.
- **Reset total** (mulai dari nol): `make clean` lalu ulangi dari langkah 1.

---

## Catatan Desain

- **CDC** dipilih agar `product-backend` tidak perlu tahu soal read model (dipisah
  bersih). Transform Debezium `unwrap` (ExtractNewRecordState) → pesan = baris flat.
- **product-search** menyimpan dokumen produk di OpenSearch; nama brand/kategori
  di-resolve saat baca dari cache in-memory (diisi dari topik `brands`/`categories`)
  → rename brand langsung tercermin tanpa reindex.
- **n-gram + `match_phrase`** di OpenSearch meniru `LIKE '%...%'` (substring panjang
  berapa pun) tetapi terindeks sehingga cepat.
- **Eventual consistency**: read model OpenSearch near-real-time (~1 dtk refresh +
  latensi CDC) — karakteristik wajar CQRS.
```
