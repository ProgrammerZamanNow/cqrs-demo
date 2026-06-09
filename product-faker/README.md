# product-faker

Generator data dummy untuk **product-backend**. Membuat **brand**, **category**,
lalu **product** dalam jumlah besar dengan memanggil REST API product-backend.

Bukan bagian dari Docker — ini hanya tool sekali jalan untuk mengisi data.

> Bagian dari [CQRS Demo](../README.md). Data yang dibuat ke product-backend
> (PostgreSQL) akan **mengalir otomatis** ke OpenSearch via CDC (Debezium → Kafka →
> product-search). Untuk demo "PostgreSQL lambat", generate banyak: `PRODUCT_COUNT=1000000`.

## Prasyarat
- [Bun](https://bun.sh) terpasang.
- product-backend sedang berjalan dan dapat diakses (default `http://localhost:8080`).

## Install
```bash
bun install
```

## Menjalankan
```bash
bun run index.ts
# atau
bun run start
```

Default: 100 brand, 50 category, 100.000 product.

## Konfigurasi (environment variable)
| Variable        | Default                 | Keterangan                              |
|-----------------|-------------------------|-----------------------------------------|
| `BASE_URL`      | `http://localhost:8080` | Alamat product-backend                  |
| `BRAND_COUNT`   | `100`                   | Jumlah brand                            |
| `CATEGORY_COUNT`| `50`                    | Jumlah category                         |
| `PRODUCT_COUNT` | `100000`                | Jumlah product                          |
| `CONCURRENCY`   | `64`                    | Request paralel saat membuat product    |
| `DRY_RUN`       | -                       | `1` untuk menguji logika tanpa hit API  |

Contoh:
```bash
# generate lebih sedikit ke server lain
BASE_URL=http://localhost:9090 PRODUCT_COUNT=1000 bun run index.ts

# uji cepat tanpa memanggil API
DRY_RUN=1 PRODUCT_COUNT=2000 bun run index.ts
```

## Catatan
- Nama brand & category dibuat unik (ditambah indeks) agar tidak kena konflik 409.
- SKU product berurutan: `SKU-0000001`, `SKU-0000002`, ...
- Harga acak 10.000–5.000.000 (IDR), stok acak 0–500.
- Gambar tidak dikirim → backend memakai URL dummy default.
