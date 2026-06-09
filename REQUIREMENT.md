# Product Service — Requirement Document

> Status: **Draft** · Versi: 0.1 · Tanggal: 2026-06-09

## 1. Ringkasan

`product-service` adalah backend service untuk **product management**. Service ini
bertanggung jawab mengelola data produk beserta kategori, harga, dan stok, serta
menyediakan REST API yang dikonsumsi oleh sistem lain (frontend, mobile, atau
service internal lainnya).

## 2. Tujuan & Lingkup

### 2.1 Tujuan
- Menyediakan sumber kebenaran (source of truth) untuk data produk.
- Menyediakan operasi CRUD produk dan kategori yang konsisten.
- Mendukung pencarian, filter, dan pagination produk.

### 2.2 Dalam Lingkup (In Scope)
- Manajemen produk (create, read, update, delete).
- Manajemen kategori produk.
- Manajemen stok dasar (jumlah stok per produk).
- Pencarian & filter produk.

### 2.3 Di Luar Lingkup (Out of Scope)
- Autentikasi & otorisasi user (**tidak ada auth** di service ini).
- Proses checkout / order / pembayaran.
- Manajemen supplier & purchasing.
- Audit trail / event publishing (CQRS event store).
- Upload gambar produk — untuk sekarang memakai **URL dummy**.

## 3. Tech Stack

| Komponen        | Pilihan                          |
|-----------------|----------------------------------|
| Bahasa          | Java 25                          |
| Framework       | Spring Boot 4.x                  |
| Database        | PostgreSQL                       |
| Akses Data      | Spring Data JPA / Hibernate      |
| Migrasi DB      | Flyway                           |
| Build Tool      | Maven                            |
| API Style       | REST (JSON)                      |
| Dokumentasi API | OpenAPI / Swagger (springdoc)    |

## 4. Functional Requirements

### 4.1 Kategori (Category)
- **FR-CAT-01** — Membuat kategori baru (nama, deskripsi).
- **FR-CAT-02** — Melihat daftar kategori (dengan pagination).
- **FR-CAT-03** — Melihat detail satu kategori berdasarkan ID.
- **FR-CAT-04** — Memperbarui kategori.
- **FR-CAT-05** — Menghapus kategori (ditolak bila masih ada produk terkait).

### 4.2 Brand (Merek)
- **FR-BRD-01** — Membuat brand baru (nama, deskripsi).
- **FR-BRD-02** — Melihat daftar brand (dengan pagination).
- **FR-BRD-03** — Melihat detail satu brand berdasarkan ID.
- **FR-BRD-04** — Memperbarui brand.
- **FR-BRD-05** — Menghapus brand (ditolak bila masih ada produk terkait).

### 4.3 Produk (Product)
- **FR-PRD-01** — Membuat produk baru (nama, deskripsi, SKU, harga, stok, kategori, brand, gambar).
- **FR-PRD-02** — Melihat daftar produk dengan pagination & sorting.
- **FR-PRD-03** — Melihat detail produk berdasarkan ID.
- **FR-PRD-04** — Memperbarui data produk.
- **FR-PRD-05** — Menghapus produk (**hard delete**).
- **FR-PRD-06** — Keyword search produk pada `name` & `description` (partial, case-insensitive).
- **FR-PRD-07** — Filter produk berdasarkan kategori (multi), brand (multi), rentang harga, dan ketersediaan stok.
- **FR-PRD-08** — SKU harus unik di seluruh produk.
- **FR-PRD-09** — Gambar produk disimpan sebagai URL; bila kosong memakai URL dummy default.
- **FR-PRD-10** — Multi-sort hasil pencarian: `name`, `price`, `stock`, `createdAt`, `updatedAt`.
- **FR-PRD-11** — Mengembalikan **facet** (kategori, brand, rentang harga, ketersediaan) beserta
  jumlah produk per item facet, dihitung dari hasil filter aktif (drill-down).

### 4.4 Stok (Stock)
- **FR-STK-01** — Menambah / mengurangi jumlah stok produk.
- **FR-STK-02** — Stok tidak boleh bernilai negatif.

## 5. Data Model (Draft)

Primary key seluruh tabel memakai **UUID**.

### 5.1 `categories`
| Kolom        | Tipe          | Keterangan                        |
|--------------|---------------|-----------------------------------|
| id           | UUID          | Primary key                       |
| name         | VARCHAR(100)  | Unik, wajib                       |
| description  | TEXT          | Opsional                          |
| created_at   | TIMESTAMP     | Waktu dibuat                      |
| updated_at   | TIMESTAMP     | Waktu diperbarui                  |

### 5.2 `brands`
| Kolom        | Tipe          | Keterangan                        |
|--------------|---------------|-----------------------------------|
| id           | UUID          | Primary key                       |
| name         | VARCHAR(100)  | Unik, wajib                       |
| description  | TEXT          | Opsional                          |
| created_at   | TIMESTAMP     | Waktu dibuat                      |
| updated_at   | TIMESTAMP     | Waktu diperbarui                  |

### 5.3 `products`
| Kolom        | Tipe          | Keterangan                        |
|--------------|---------------|-----------------------------------|
| id           | UUID          | Primary key                       |
| sku          | VARCHAR(50)   | Unik, wajib                       |
| name         | VARCHAR(150)  | Wajib                             |
| description  | TEXT          | Opsional                          |
| price        | NUMERIC(15,2) | Wajib, >= 0                       |
| stock        | INTEGER       | Default 0, >= 0                   |
| image_url    | VARCHAR(500)  | URL gambar; default dummy bila kosong |
| category_id  | UUID (FK)     | Relasi ke `categories.id`         |
| brand_id     | UUID (FK)     | Relasi ke `brands.id`             |
| created_at   | TIMESTAMP     | Waktu dibuat                      |
| updated_at   | TIMESTAMP     | Waktu diperbarui                  |

## 6. REST API

Base path: `/api`

### 6.1 Standar Envelope Respons

Semua respons API dibungkus dalam envelope standar berikut.

**Respons sukses — data tunggal (object):**
```json
{
  "data": { /* object */ },
  "metadata": {
    "processTimeMs": 12
  }
}
```

**Respons sukses — data list dengan pagination:**
```json
{
  "data": [ /* array of object */ ],
  "paging": {
    "page": 0,
    "size": 10,
    "totalElement": 135,
    "totalPage": 14
  },
  "metadata": {
    "processTimeMs": 27
  }
}
```

**Respons error:**
```json
{
  "error": "price must be greater than or equal to 0",
  "metadata": {
    "processTimeMs": 5
  }
}
```

Aturan umum:
- Field `data` **selalu** ada pada respons sukses (object untuk detail, array untuk
  list; bernilai `null` pada operasi yang tidak mengembalikan resource, mis. DELETE).
- Field `paging` **hanya** muncul pada endpoint list yang mendukung pagination.
- Field `facets` **hanya** muncul pada endpoint Search Product (lihat 6.3.2).
- Field `error` **hanya** muncul pada respons gagal (HTTP 4xx/5xx), berupa **string**
  berisi pesan error yang siap ditampilkan. Bila ada beberapa error validasi, pesan
  digabung menjadi satu string dipisah `; `.
- Field `metadata` **selalu** ada di **setiap** respons (sukses maupun gagal,
  termasuk DELETE), berisi `processTimeMs` yaitu lama proses request di backend
  dalam **milidetik**.

**Konvensi global:**
- **Content-Type** selalu `application/json` (request & response), encoding UTF-8.
- **Timestamp** (`createdAt`, `updatedAt`) berupa **Long epoch time (milidetik)** UTC.
  FE yang menampilkan sesuai timezone masing-masing.
- **Mata uang** semua nilai `price` dalam **IDR** (tanpa kode mata uang di payload).

**Metadata waktu proses (`processTimeMs`):**
- Dihitung dari awal request masuk hingga response siap dikirim, di sisi backend.
- Diukur secara terpusat (mis. via `Filter`/`Interceptor` atau `HandlerInterceptor`),
  bukan per-controller, agar konsisten di semua endpoint.
- Selain di body, durasi yang sama juga dikirim lewat response header
  `X-Process-Time-Ms` untuk memudahkan monitoring/observability.

Catatan implementasi (contoh): sebuah `OncePerRequestFilter` mencatat waktu mulai
saat request masuk, lalu mengisi `processTimeMs` dan header `X-Process-Time-Ms`
sebelum response dikirim. Nilai pada contoh-contoh di bawah hanyalah ilustrasi.

> Pada contoh per-endpoint di bawah, field `metadata` **dihilangkan demi keringkasan**,
> namun pada respons sebenarnya selalu ada.

**Query parameter pagination & sorting (standar):**
| Param   | Tipe   | Default | Keterangan                                   |
|---------|--------|---------|----------------------------------------------|
| `page`  | int    | 0       | Nomor halaman (0-based)                       |
| `size`  | int    | 10      | Jumlah item per halaman (maks. 100)           |
| `sort`  | string | -       | Format `field,asc|desc` (mis. `name,asc`)     |

Aturan validasi pagination & sorting:
- `size > 100` → **ditolak (400)**.
- `sort` ke field yang tidak diizinkan → **diabaikan**, memakai sort default.
- `page` di luar range (melebihi jumlah halaman) → bukan error; `data` dikembalikan
  **kosong** (`[]`) dengan `paging` tetap terisi.

**HTTP status standar:**
| Skenario                    | Status |
|-----------------------------|--------|
| GET / PUT / PATCH sukses    | 200    |
| POST sukses (resource baru) | 201    |
| DELETE sukses               | 200    |
| Validasi gagal              | 400    |
| Resource tidak ditemukan    | 404    |
| Konflik (mis. SKU duplikat) | 409    |
| Error server                | 500    |

> Catatan: DELETE memakai **200** (bukan 204) supaya tetap membawa body berisi
> `metadata.processTimeMs`. Body: `{ "data": null, "metadata": { "processTimeMs": N } }`.

---

### 6.2 Category API

#### 6.2.1 Create Category
`POST /api/categories` → **201**

Request:
```json
{
  "name": "Electronics",
  "description": "Electronic devices and accessories"
}
```
Response:
```json
{
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Electronics",
    "description": "Electronic devices and accessories",
    "createdAt": 1780999200000,
    "updatedAt": 1780999200000
  }
}
```

Aturan validasi (berlaku untuk Create & Update Category):
- `name` **wajib**, maksimal **100 karakter**, unik (konflik → **409**).
- `description` opsional.

#### 6.2.2 List Category
`GET /api/categories?page=0&size=10&sort=name,asc` → **200**

Response:
```json
{
  "data": [
    {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "name": "Electronics",
      "description": "Electronic devices and accessories",
      "createdAt": 1780999200000,
      "updatedAt": 1780999200000
    }
  ],
  "paging": {
    "page": 0,
    "size": 10,
    "totalElement": 1,
    "totalPage": 1
  }
}
```

#### 6.2.3 Get Category Detail
`GET /api/categories/{id}` → **200** (atau **404** bila tidak ada)

Response:
```json
{
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Electronics",
    "description": "Electronic devices and accessories",
    "createdAt": 1780999200000,
    "updatedAt": 1780999200000
  }
}
```

#### 6.2.4 Update Category
`PUT /api/categories/{id}` → **200**

Request:
```json
{
  "name": "Electronics & Gadgets",
  "description": "Updated description"
}
```
Response: sama seperti Get Category Detail (data terbaru).

#### 6.2.5 Delete Category
`DELETE /api/categories/{id}` → **200**

Response:
```json
{
  "data": null,
  "metadata": { "processTimeMs": 8 }
}
```
Catatan: ditolak dengan **409** bila masih ada produk yang memakai kategori ini.

---

### 6.3 Product API

#### 6.3.1 Create Product
`POST /api/products` → **201**

Request:
```json
{
  "sku": "SKU-0001",
  "name": "Wireless Mouse",
  "description": "2.4GHz wireless mouse",
  "price": 150000.00,
  "stock": 50,
  "imageUrl": "https://dummyimage.com/600x400/cccccc/000000&text=No+Image",
  "categoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "brandId": "7d8e9f10-2222-4333-8444-555566667777"
}
```
Response:
```json
{
  "data": {
    "id": "9b2f1c3a-7d4e-4a1b-9c8d-2e5f6a7b8c9d",
    "sku": "SKU-0001",
    "name": "Wireless Mouse",
    "description": "2.4GHz wireless mouse",
    "price": 150000.00,
    "stock": 50,
    "imageUrl": "https://dummyimage.com/600x400/cccccc/000000&text=No+Image",
    "category": {
      "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "name": "Electronics"
    },
    "brand": {
      "id": "7d8e9f10-2222-4333-8444-555566667777",
      "name": "Logitech"
    },
    "createdAt": 1780999200000,
    "updatedAt": 1780999200000
  }
}
```

Aturan validasi:
- `sku` wajib, unik (konflik → **409**).
- `name` wajib.
- `price` wajib, `>= 0`.
- `stock` `>= 0` (default 0 bila tidak diisi).
- `imageUrl` opsional; bila kosong server mengisi URL dummy default.
- `categoryId` wajib & harus merujuk kategori yang ada (tidak ada → **400/404**).
- `brandId` wajib & harus merujuk brand yang ada (tidak ada → **400/404**).

#### 6.3.2 Search / List Product
`GET /api/products` → **200**

Endpoint ini meniru pencarian produk di toko online: mendukung **keyword search**,
banyak **filter**, **sorting**, **pagination**, dan mengembalikan **facet** beserta
jumlah produk di tiap item facet.

> Lingkup demo: filter & facet hanya memakai field yang sudah ada pada produk
> (`name`, `description`, `price`, `stock`, `category`, `brand`). Facet dihitung
> via agregasi SQL (PostgreSQL `GROUP BY`).

**Query parameter — keyword & filter:**
| Param         | Tipe    | Multi | Keterangan                                                        |
|---------------|---------|-------|-------------------------------------------------------------------|
| `keyword`     | string  | tidak | Cari di `name` & `description` (partial, case-insensitive)         |
| `categoryId`  | uuid    | ya    | Filter kategori; boleh diulang → `categoryId=..&categoryId=..` (OR)|
| `brandId`     | uuid    | ya    | Filter brand; boleh diulang → `brandId=..&brandId=..` (OR)         |
| `minPrice`    | decimal | tidak | Harga minimum (inklusif)                                          |
| `maxPrice`    | decimal | tidak | Harga maksimum (inklusif)                                         |
| `availability`| enum    | tidak | `IN_STOCK` (stock > 0) atau `OUT_OF_STOCK` (stock = 0)             |

Semua filter bersifat opsional dan digabung dengan **AND** antar dimensi (nilai
ganda di dimensi yang sama, mis. beberapa `categoryId`, digabung dengan **OR**).

**Query parameter — pagination & sorting:**
| Param  | Tipe   | Default      | Keterangan                                              |
|--------|--------|--------------|---------------------------------------------------------|
| `page` | int    | 0            | Nomor halaman (0-based)                                  |
| `size` | int    | 10           | Item per halaman (maks. 100)                            |
| `sort` | string | `name,asc`   | Field + arah. Bisa lebih dari satu (diulang)            |

Field `sort` yang didukung: `name`, `price`, `stock`, `createdAt`, `updatedAt`
(masing-masing `asc`/`desc`). Contoh multi-sort: `sort=price,asc&sort=name,asc`.

Contoh request:
```
GET /api/products?keyword=mouse
    &categoryId=3fa85f64-5717-4562-b3fc-2c963f66afa6
    &brandId=7d8e9f10-2222-4333-8444-555566667777
    &minPrice=100000&maxPrice=500000
    &availability=IN_STOCK
    &page=0&size=10&sort=price,asc
```

**Response** — selain `data`, `paging`, dan `metadata`, ada field **`facets`**:
```json
{
  "data": [
    {
      "id": "9b2f1c3a-7d4e-4a1b-9c8d-2e5f6a7b8c9d",
      "sku": "SKU-0001",
      "name": "Wireless Mouse",
      "description": "2.4GHz wireless mouse",
      "price": 150000.00,
      "stock": 50,
      "imageUrl": "https://dummyimage.com/600x400/cccccc/000000&text=No+Image",
      "category": {
        "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        "name": "Electronics"
      },
      "brand": {
        "id": "7d8e9f10-2222-4333-8444-555566667777",
        "name": "Logitech"
      },
      "createdAt": 1780999200000,
      "updatedAt": 1780999200000
    }
  ],
  "paging": {
    "page": 0,
    "size": 10,
    "totalElement": 1,
    "totalPage": 1
  },
  "facets": {
    "categories": [
      { "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "name": "Electronics",  "count": 12, "selected": true },
      { "id": "5c1d2e3f-1111-4222-8333-444455556666", "name": "Accessories", "count": 7,  "selected": false }
    ],
    "brands": [
      { "id": "7d8e9f10-2222-4333-8444-555566667777", "name": "Logitech", "count": 9, "selected": true },
      { "id": "8e9f1011-3333-4444-8555-666677778888", "name": "Razer",    "count": 6, "selected": false }
    ],
    "priceRanges": [
      { "min": 0,      "max": 100000,  "label": "< 100.000",          "count": 3,  "selected": false },
      { "min": 100000, "max": 500000,  "label": "100.000 - 500.000",  "count": 11, "selected": false },
      { "min": 500000, "max": null,    "label": "> 500.000",          "count": 5,  "selected": false }
    ],
    "availability": [
      { "value": "IN_STOCK",     "count": 18, "selected": false },
      { "value": "OUT_OF_STOCK", "count": 1,  "selected": false }
    ]
  },
  "metadata": {
    "processTimeMs": 35
  }
}
```

**Aturan facet:**
- `facets.categories` — jumlah produk per kategori (hanya kategori yang punya
  produk pada hasil filter saat ini), diurutkan `count` desc.
- `facets.brands` — jumlah produk per brand (hanya brand yang punya produk pada
  hasil filter saat ini), diurutkan `count` desc.
- `facets.priceRanges` — bucket harga **statis** (default: `<100rb`,
  `100rb–500rb`, `>500rb`) beserta jumlah produk per bucket. `max: null` berarti
  tak terbatas ke atas.
- `facets.availability` — jumlah produk `IN_STOCK` (stock > 0) vs `OUT_OF_STOCK`
  (stock = 0).
- Setiap item facet punya flag `selected` (`true` bila nilai itu sedang dipilih di
  filter request), agar FE bisa menampilkan checkbox dalam keadaan tercentang.
- Bila hasil pencarian kosong (`data: []`), tiap dimensi facet juga dikembalikan
  sebagai **array kosong** (`categories: []`, `brands: []`, dst).

**Aturan drill-down (PALING PENTING):**
Count pada sebuah facet dihitung dengan menerapkan semua filter aktif **KECUALI
filter dari dimensi facet itu sendiri**. Tujuannya: item lain di dimensi yang sama
**tidak hilang** setelah salah satu dipilih, sehingga user masih bisa melihat &
berpindah/menambah pilihan.

Contoh skenario (persis kasus "sepatu"):
1. User search `keyword=sepatu` (belum memilih brand). Facet brand:
   ```
   Adidas (10), Nike (20), Puma (30)
   ```
2. User mencentang **Nike** → FE mengirim `keyword=sepatu&brandId=<id-nike>`.
3. Hasil **produk** (`data`) sekarang hanya produk Nike. **TETAPI** `facets.brands`
   tetap menampilkan **ketiga** brand, karena facet brand mengabaikan filter brand:
   ```
   Adidas (10), Nike (20, selected=true), Puma (30)
   ```
   Adidas & Puma **tidak hilang**, count-nya pun tidak berubah (masih dihitung dari
   `keyword=sepatu` saja).
4. Sebaliknya, facet **dimensi lain** (mis. `categories`, `priceRanges`,
   `availability`) **ikut** memperhitungkan `brandId=Nike` — karena bagi mereka,
   brand bukan dimensinya sendiri.

Implikasi implementasi: jumlah query agregasi = 1 untuk hasil produk + 1 per dimensi
facet, di mana tiap query facet memakai filter aktif **minus** filter dimensinya
sendiri. Bila ada beberapa nilai dipilih dalam satu dimensi (mis. Nike **dan**
Adidas), antar nilai itu digabung **OR** dan tetap dikecualikan saat menghitung
facet dimensi tersebut.

- Untuk demo, facet selalu dikembalikan. (Opsional ke depan: query param
  `facet=categories,brands,priceRanges,availability` untuk memilih facet yang dihitung.)

#### 6.3.3 Get Product Detail
`GET /api/products/{id}` → **200** (atau **404**)

Response: sama seperti object pada Create Product.

#### 6.3.4 Update Product
`PUT /api/products/{id}` → **200**

Request:
```json
{
  "sku": "SKU-0001",
  "name": "Wireless Mouse Pro",
  "description": "Updated description",
  "price": 175000.00,
  "stock": 40,
  "imageUrl": "https://dummyimage.com/600x400/cccccc/000000&text=No+Image",
  "categoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "brandId": "7d8e9f10-2222-4333-8444-555566667777"
}
```
Response: sama seperti Get Product Detail (data terbaru).

#### 6.3.5 Update Stock
`PATCH /api/products/{id}/stock` → **200**

Request:
```json
{
  "quantity": 10,
  "type": "INCREASE"
}
```
- `type`: `INCREASE` atau `DECREASE`.
- `quantity`: `> 0`.
- `DECREASE` yang membuat stok negatif ditolak dengan **400**.

**Konkurensi:** update stok dilakukan dalam satu transaksi dengan **`SELECT ... FOR
UPDATE`** untuk mengunci baris produk sebelum dibaca-diubah-disimpan, sehingga aman
dari race condition saat beberapa request stok datang bersamaan.

Response:
```json
{
  "data": {
    "id": "9b2f1c3a-7d4e-4a1b-9c8d-2e5f6a7b8c9d",
    "sku": "SKU-0001",
    "stock": 60,
    "updatedAt": 1780999500000
  }
}
```

#### 6.3.6 Delete Product
`DELETE /api/products/{id}` → **200**

Response:
```json
{
  "data": null,
  "metadata": { "processTimeMs": 8 }
}
```
Catatan: implementasi memakai **hard delete** (baris dihapus permanen dari database).

---

### 6.4 Brand API

Struktur identik dengan Category API.

#### 6.4.1 Create Brand
`POST /api/brands` → **201**

Request:
```json
{
  "name": "Logitech",
  "description": "Computer peripherals manufacturer"
}
```
Response:
```json
{
  "data": {
    "id": "7d8e9f10-2222-4333-8444-555566667777",
    "name": "Logitech",
    "description": "Computer peripherals manufacturer",
    "createdAt": 1780999200000,
    "updatedAt": 1780999200000
  }
}
```

Aturan validasi (berlaku untuk Create & Update Brand):
- `name` **wajib**, maksimal **100 karakter**, unik (konflik → **409**).
- `description` opsional.

#### 6.4.2 List Brand
`GET /api/brands?page=0&size=10&sort=name,asc` → **200**

Response:
```json
{
  "data": [
    {
      "id": "7d8e9f10-2222-4333-8444-555566667777",
      "name": "Logitech",
      "description": "Computer peripherals manufacturer",
      "createdAt": 1780999200000,
      "updatedAt": 1780999200000
    }
  ],
  "paging": {
    "page": 0,
    "size": 10,
    "totalElement": 1,
    "totalPage": 1
  }
}
```

#### 6.4.3 Get Brand Detail
`GET /api/brands/{id}` → **200** (atau **404** bila tidak ada)

Response: sama seperti object pada Create Brand.

#### 6.4.4 Update Brand
`PUT /api/brands/{id}` → **200**

Request:
```json
{
  "name": "Logitech G",
  "description": "Updated description"
}
```
Response: sama seperti Get Brand Detail (data terbaru).

#### 6.4.5 Delete Brand
`DELETE /api/brands/{id}` → **200**

Response:
```json
{
  "data": null,
  "metadata": { "processTimeMs": 8 }
}
```
Catatan: ditolak dengan **409** bila masih ada produk yang memakai brand ini.

---

### 6.5 Contoh Respons Error

Semua error memakai field `error` (string) + `metadata`.

**Validasi (400)** — satu pesan:
```json
{
  "error": "price must be greater than or equal to 0",
  "metadata": { "processTimeMs": 5 }
}
```

**Validasi (400)** — beberapa error digabung dengan `; `:
```json
{
  "error": "price must be greater than or equal to 0; name must not be blank",
  "metadata": { "processTimeMs": 6 }
}
```

**Tidak ditemukan (404):**
```json
{
  "error": "Product with id 9b2f1c3a-7d4e-4a1b-9c8d-2e5f6a7b8c9d not found",
  "metadata": { "processTimeMs": 4 }
}
```

**Konflik (409):**
```json
{
  "error": "SKU 'SKU-0001' already exists",
  "metadata": { "processTimeMs": 5 }
}
```

## 7. Non-Functional Requirements
- **NFR-01** — Validasi input di layer API (Bean Validation / `jakarta.validation`).
- **NFR-02** — Penanganan error terpusat (`@RestControllerAdvice`).
- **NFR-03** — Logging request/response dan error.
- **NFR-04** — Migrasi skema database dikelola Flyway (versioned).
- **NFR-05** — Konfigurasi lewat environment variable (12-factor).
- **NFR-06** — Unit test & integration test (Testcontainers untuk PostgreSQL).
  Lingkungan dev memakai **Podman** (bukan Docker). Testcontainers diarahkan ke
  socket Podman via `DOCKER_HOST`, dengan **Ryuk dinonaktifkan**
  (`TESTCONTAINERS_RYUK_DISABLED=true`). Lihat Lampiran A.
- **NFR-07** — Dokumentasi API otomatis via OpenAPI/Swagger UI.
- **NFR-08** — Setiap respons API menyertakan `metadata.processTimeMs` (lama proses
  request di backend, dalam milidetik) dan header `X-Process-Time-Ms`, diukur
  terpusat via filter/interceptor.
- **NFR-09** — Update stok memakai `SELECT ... FOR UPDATE` (row lock) agar aman dari
  race condition.
- **NFR-10** — Spring Boot Actuator diaktifkan (mis. `/actuator/health`,
  `/actuator/info`) untuk health check & readiness.
- **NFR-11** — Timestamp memakai **Long epoch millis** (UTC); semua payload
  `application/json` UTF-8.

## 8. Keputusan Desain (Sudah Diputuskan)
1. **Autentikasi/otorisasi** — tidak ada; tidak ditangani di service ini.
2. **Primary key** — memakai **UUID** untuk semua tabel.
3. **Multi-mata-uang** — tidak; harga single currency.
4. **Gambar produk** — belum ada upload file; memakai **URL dummy** sebagai placeholder.
5. **Delete produk** — **hard delete**.
6. **Audit trail / event publishing** — tidak diperlukan.
7. **Container runtime (dev)** — memakai **Podman**, bukan Docker.

## Lampiran A — Testcontainers di atas Podman

Lingkungan dev tidak memakai Docker. Agar Testcontainers (untuk PostgreSQL) jalan di
atas Podman pada macOS:

1. Pastikan Podman machine hidup:
   ```bash
   podman machine start
   ```
2. Arahkan Testcontainers ke socket Podman & nonaktifkan Ryuk:
   ```bash
   export DOCKER_HOST="unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')"
   export TESTCONTAINERS_RYUK_DISABLED=true
   ```
   Alternatif permanen lewat `~/.testcontainers.properties`:
   ```properties
   docker.host=unix:///<path-socket-podman>
   ryuk.disabled=true
   ```

Catatan:
- Ryuk dinonaktifkan karena pada Podman rootless di macOS sering gagal; konsekuensinya
  container sisa test perlu dibersihkan manual bila proses test mati paksa
  (`podman ps -a` lalu `podman rm`).
- Image database ditarik dari registry publik (mis. `docker.io/library/postgres:17`);
  Podman mendukung penarikan image OCI yang sama.
