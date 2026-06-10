# SLIDE — CQRS (Command Query Responsibility Segregation)

> **Catatan untuk pembuat slide (Claude Design):** File ini adalah materi sumber.
> Setiap bagian dipisah `---` = satu slide. `##` = judul slide. Bullet = poin di
> slide. Blok `> Narasi:` = catatan pembicara (boleh tidak ditampilkan). Blok
> ```mermaid``` = diagram yang sebaiknya dirender visual. Bahasa: Indonesia,
> istilah teknis dibiarkan dalam Bahasa Inggris.

---

## CQRS

### Command Query Responsibility Segregation

Memisahkan model **tulis (Command)** dari model **baca (Query)** agar tiap sisi
bisa dioptimalkan independen.

> Studi kasus: pencarian katalog produk e-commerce (PostgreSQL → OpenSearch)

> Narasi: Yang kita bahas adalah **pola CQRS** — product search hanya contoh kasus
> yang membuat masalahnya terasa nyata. Kita mulai dari masalah sederhana yang
> dialami banyak tim, lalu pelan-pelan sampai ke CQRS dan mendemokannya langsung.

---

## Konteks: Pencarian Katalog Produk

- Aplikasi punya **katalog produk** (produk, kategori, brand).
- User mengharapkan pencarian ala toko online:
  - **Keyword search** ("sepatu", "wireless mouse")
  - **Filter**: kategori, brand, rentang harga, ketersediaan stok
  - **Facet**: jumlah produk di tiap pilihan filter (Nike (20), Adidas (30)…)
  - **Sorting** & **pagination**
- Data awalnya sedikit → semua terasa cepat.

> Narasi: Di awal proyek, semua fitur ini gampang dibuat dengan satu database.
> Masalah baru muncul saat data membesar.

---

## Pendekatan Awal: Satu Database Saja

```mermaid
flowchart LR
    U([User]) -->|cari + filter| BE[Backend]
    BE -->|LIKE + GROUP BY| PG[(PostgreSQL)]
    PG -->|hasil| BE
    BE --> U
```

- **Write & read** dilayani **PostgreSQL** yang sama.
- Keyword: `WHERE LOWER(name) LIKE '%kata%' OR LOWER(description) LIKE '%kata%'`
- Facet: beberapa query `GROUP BY` (per kategori, brand, harga, stok).

> Narasi: Arsitektur paling sederhana dan paling umum. Tidak ada yang salah —
> sampai datanya jadi jutaan baris.

---

## Masalah: Lambat di Skala Besar

Hasil pengukuran nyata (waktu proses query di backend):

| Jumlah produk | Keyword search + facet |
|---------------|------------------------|
| 100.000       | ~0,3 detik             |
| **1.000.000** | **~3,2 detik** 🐌      |

- Di 1 juta data, satu pencarian bisa **~3 detik**.
- Makin banyak data → makin lambat (mendekati linear, lalu memburuk).

> Narasi: Tiga detik untuk satu kali ketik pencarian itu fatal untuk UX. Dan ini
> baru 1 juta — bayangkan 10 juta.

---

## Kenapa Bisa Selambat Itu?

- **`LIKE '%kata%'` (leading wildcard)** → index **tidak bisa dipakai** →
  **sequential scan** seluruh tabel.
- **Facet** = beberapa query agregasi `GROUP BY`, **masing-masing full-scan**.
- Biaya ≈ **(jumlah query facet) × (scan seluruh baris)**.

```mermaid
flowchart LR
    Q["keyword + facet"] --> S1["seq scan: hits"]
    Q --> S2["seq scan: facet kategori"]
    Q --> S3["seq scan: facet brand"]
    Q --> S4["seq scan: facet harga"]
    Q --> S5["seq scan: facet stok"]
    S1 & S2 & S3 & S4 & S5 --> R["≈ 3 detik @ 1 juta"]
```

> Narasi: Database OLTP (PostgreSQL) dioptimalkan untuk transaksi tulis yang
> ternormalisasi — bukan untuk full-text search + agregasi berat. Tapi sebelum
> buru-buru ganti arsitektur, tahan dulu: coba optimalkan PostgreSQL-nya.

---

## Tahan Dulu: Optimalkan Database, BARU CQRS

Sebelum menambah arsitektur baru, **optimalkan dulu yang sudah ada.**

- CQRS itu **mahal**: +Kafka, +Debezium, +OpenSearch, +eventual consistency,
  dua datastore untuk dijaga & dimonitor.
- Urutan yang benar:
  1. **Optimalkan database dulu** — index, query, skema.
  2. **Ukur ulang.**
  3. **Hanya jika sudah mentok** → pertimbangkan CQRS.
- Lompat ke CQRS karena **butuh**, bukan karena tren.

> Narasi: Ini disiplin engineering. Banyak tim langsung bilang "butuh Kafka + CQRS"
> padahal satu index saja sudah cukup. Mari buktikan dulu: seberapa jauh PostgreSQL
> bisa dioptimalkan?

---

## Optimasi: Index Trigram (`pg_trgm`)

Masalah keyword: `LIKE '%kata%'` (leading wildcard) → B-tree tak terpakai → seq scan.
Solusinya ada **di dalam PostgreSQL sendiri**:

```sql
CREATE EXTENSION pg_trgm;
CREATE INDEX idx_products_name_trgm
    ON products USING GIN (LOWER(name) gin_trgm_ops);
CREATE INDEX idx_products_description_trgm
    ON products USING GIN (LOWER(description) gin_trgm_ops);
```

- **Trigram** = n-gram 3 huruf — "mouse" → `mou, ous, use`, disimpan di **GIN index**.
- **Konsep yang persis sama** dengan analyzer n-gram OpenSearch.
- Query `LIKE '%..%'` yang **sudah ada otomatis** memakai index — **nol perubahan kode aplikasi**.

> Narasi: Perhatikan, ini bukan menambal aplikasi — cukup satu migration SQL.
> PostgreSQL punya "search engine mini" di dalamnya. Ini optimasi paling murah.

---

## Hasil Optimasi: Search Murni Melesat

Keyword search **tanpa facet** (search murni), 1 juta produk — `processTimeMs` (avg):

| Keyword (match)      | PG naif (seq scan) | PG + trigram | Speedup |
|----------------------|-------------------:|-------------:|--------:|
| `titanium` (874)     | 1033 ms | **13 ms** | **~79×** |
| `handcrafted` (39rb) |  869 ms |   235 ms  |   ~4×   |
| `rubber` (80rb)      |  822 ms |   291 ms  |   ~3×   |
| `ergonomic` (135rb)  | 1118 ms |   544 ms  |   ~2×   |

- Keyword **selektif**: dari **~1 dtk → 13 ms** — hampir secepat search engine.
- Bukti seq scan: PG naif **konstan ~0,8–1,1 dtk** apa pun keyword-nya (biaya tak
  peduli jumlah match) — ciri khas sequential scan; trigram menskala dengan jumlah match.

> Narasi: Pelajaran besar pertama — jangan remehkan optimasi DB. Satu index: 850 ms
> jadi 11 ms. Kalau kebutuhanmu hanya keyword search selektif, **selesai di sini —
> tak perlu CQRS.**

---

## Tapi Optimasi Punya Batas

Trigram hebat untuk keyword, **tapi tidak menyelesaikan semuanya**:

- **Facet tetap berat** — agregasi `GROUP BY` tetap memproses semua baris yang cocok:
  - PG + trigram **+ facet**: `titanium` jadi **357 ms**, `ergonomic` **1759 ms**
    (search murninya tadi cuma 13 ms & 544 ms — facet-lah biang lambatnya).
  - OpenSearch + facet: **12–29 ms** — facet **nyaris gratis** (satu pass agregasi).
- **Keyword umum** (cocok >10% data) → bitmap tetap menyentuh hampir seluruh tabel.
- **Beban menumpuk di DB OLTP yang sama**: GIN index besar (~157 MB) + overhead tulis;
  read berat tarik-menarik resource dengan transaksi write.

```mermaid
flowchart LR
    O["Optimasi DB<br/>(pg_trgm)"] --> G1["Keyword search:<br/>SELESAI ✓"]
    O --> G2["Facet berat: TETAP ✗"]
    O --> G3["Skala & beban OLTP: TETAP ✗"]
    G2 & G3 --> C["→ di sinilah CQRS<br/>baru masuk akal"]
```

> Narasi: Inilah titik mentok. Search sudah cepat, tapi facet + skala + beban OLTP
> tetap masalah. DI SINILAH optimasi habis dan CQRS benar-benar dibutuhkan —
> bukan sebelumnya.

---

## Akar Masalah: Satu Model untuk Dua Kebutuhan

- **Write** butuh: normalisasi, konsistensi, transaksi (OLTP).
- **Read/Search** butuh: full-text, agregasi facet, kecepatan baca.
- Keduanya **tarik-menarik** dalam satu model data yang sama.

> Optimasi untuk write ≠ optimasi untuk read.

> Narasi: Ini insight kuncinya. Selama write dan read berbagi satu model, kita
> selalu berkompromi. Solusinya: pisahkan keduanya.

---

## Solusi: CQRS

**C**ommand **Q**uery **R**esponsibility **S**egregation

- Pisahkan **model tulis (Command)** dari **model baca (Query)**.
- Tiap sisi dioptimalkan **independen**, pakai teknologi yang paling cocok.

```mermaid
flowchart LR
    subgraph CMD["COMMAND — Write"]
        W[Write Model<br/>PostgreSQL]
    end
    subgraph QRY["QUERY — Read"]
        R[Read Model<br/>OpenSearch]
    end
    W ==>|sinkronisasi| R
    A([Tulis data]) --> W
    R --> B([Baca / search])
```

> Narasi: CQRS bukan teknologi, tapi pola. Inti idenya satu kalimat: data ditulis
> ke satu model, dibaca dari model lain yang sudah dioptimalkan untuk baca.

---

## CQRS: Manfaat & Konsekuensi

**Manfaat**
- Read model bisa pakai engine khusus (search engine) → baca super cepat.
- Write model tetap bersih & ternormalisasi.
- Skala read & write bisa independen.

**Konsekuensi (trade-off)**
- **Eventual consistency**: read model menyusul write (tidak instan).
- Lebih banyak komponen → kompleksitas operasional naik.

> Narasi: Tidak ada makan siang gratis. CQRS menukar "konsistensi langsung &
> kesederhanaan" dengan "kecepatan baca & skalabilitas". Untuk fitur search,
> tukar-menukar ini hampir selalu worth it.

---

## Arsitektur Demo Kita

```mermaid
flowchart LR
    FE[product-frontend<br/>nginx + SPA]

    subgraph WRITE["COMMAND (Write)"]
        BE[product-backend<br/>Spring Boot]
        PG[(PostgreSQL)]
        BE --> PG
    end

    subgraph PIPE["Sinkronisasi — CDC"]
        DBZ[Debezium<br/>Kafka Connect]
        K[(Kafka)]
        DBZ --> K
    end

    subgraph READ["QUERY (Read)"]
        PS[product-search<br/>Spring Boot]
        OS[(OpenSearch)]
        PS --> OS
    end

    PG -->|WAL logical| DBZ
    K --> PS
    FE -->|/api/products| BE
    FE -->|/api/products/_search| PS
```

- **Write**: `product-backend` → PostgreSQL (sumber kebenaran).
- **Sync**: Debezium membaca perubahan → Kafka.
- **Read**: `product-search` mengonsumsi → meng-index ke OpenSearch → melayani `/_search`.
- **Frontend** bisa memilih query ke **PostgreSQL** atau **OpenSearch**.

> Narasi: Inilah peta besarnya. Tiga slide berikutnya membedah tiap bagian:
> sinkronisasi (CDC), read model (OpenSearch), dan trade-off konsistensi.

---

## Sinkronisasi via CDC (Change Data Capture)

Bagaimana data write sampai ke read model **tanpa** backend tahu soal read model?

```mermaid
sequenceDiagram
    participant BE as product-backend
    participant PG as PostgreSQL
    participant DBZ as Debezium
    participant K as Kafka
    participant PS as product-search
    participant OS as OpenSearch
    BE->>PG: INSERT/UPDATE produk (commit)
    PG-->>DBZ: perubahan via WAL (logical)
    DBZ->>K: publish event (topik produk/brand/kategori)
    K->>PS: consume event
    PS->>OS: index / update dokumen
    Note over OS: bisa dicari ~1 detik kemudian
```

- **Debezium** membaca **WAL** PostgreSQL (bukan polling, bukan dual-write).
- `product-backend` **tidak diubah** — tetap menulis ke PostgreSQL seperti biasa.
- Decoupling bersih: write tak peduli ada berapa read model.

> Narasi: CDC adalah "kabel" CQRS. Karena membaca WAL, ia menangkap SEMUA
> perubahan tanpa mengubah aplikasi penulis. Ini kunci pemisahan yang bersih.

---

## Read Model: Kenapa OpenSearch?

Search engine memang dirancang untuk pekerjaan ini:

- **Full-text & substring** cepat (terindeks), bukan sequential scan.
- **Facet = aggregations native** → hits + semua facet dalam **satu** query.
- **Relevansi, typo-tolerance**, skala horizontal.

| Aspek                | PostgreSQL + `LIKE` | OpenSearch          |
|----------------------|---------------------|---------------------|
| Keyword substring    | seq scan (lambat)   | terindeks (cepat)   |
| Facet + count        | banyak `GROUP BY`   | 1 query agregasi    |
| Waktu @ 1 juta       | ~3 detik            | puluhan milidetik   |

> Narasi: Kita tidak "memperbaiki" PostgreSQL — kita memberi pekerjaan search ke
> alat yang memang ahlinya, sambil PostgreSQL tetap jadi sumber kebenaran.

---

## Teknik #1: n-gram = `LIKE` tapi Cepat

Kita harus meniru perilaku `LIKE '%kata%'` (substring di mana saja).

- Field `name`/`description` di-index dengan **analyzer n-gram** (2–3 huruf).
- Query memakai **`match_phrase`** → n-gram harus berurutan → cocok substring
  **panjang berapa pun** (mirip `LIKE`), tetapi **terindeks → cepat**.
- Sama prinsipnya dengan **`pg_trgm`** (trigram) di PostgreSQL.

```
"wireless"  →  [wi, wir, ire, rel, ele, les, ess, ...]
match_phrase: n-gram harus bersebelahan  →  substring "wireless" ketemu
```

> Hasil demo: total hasil **identik** dengan `LIKE` PostgreSQL (parity terverifikasi),
> tanpa false positive — tapi jauh lebih cepat.

---

## Teknik #2: Facet Drill-Down

"Pilih satu brand, brand lain jangan hilang."

- Satu request → hits + semua facet.
- Tiap facet dihitung dengan semua filter aktif **KECUALI** filter dimensinya
  sendiri (`post_filter` + `filter` aggregation per dimensi).

```mermaid
flowchart TB
    Q["Search: keyword=sepatu, brand=Nike"]
    Q --> H["Hits: produk Nike saja"]
    Q --> FB["Facet BRAND: Nike(20), Adidas(30), Puma(10)<br/>(abaikan filter brand → semua tetap muncul)"]
    Q --> FC["Facet KATEGORI: ikut filter brand=Nike"]
```

> Narasi: Ini perilaku standar e-commerce. Di OpenSearch jadi satu query elegan;
> di PostgreSQL butuh banyak query terpisah.

---

## Trade-off: Eventual Consistency

- Read model **menyusul** write, tidak instan.
- Lag total ≈ Debezium + Kafka + indexing + **refresh OpenSearch (~1 dtk)**.
- Praktiknya: tulis di backend → **~1 detik** kemudian muncul di `/_search`.

```mermaid
flowchart LR
    W["Commit di PostgreSQL<br/>(t = 0)"] --> L["Muncul di OpenSearch<br/>(t ≈ 1 dtk)"]
```

- Untuk **search katalog**, lag 1 detik **sangat dapat diterima**.
- Untuk data yang butuh konsistensi kuat (mis. saldo) → tetap baca dari write model.

> Narasi: Penting jujur soal ini. CQRS = eventual consistency. Cocok untuk search,
> tidak cocok untuk semua hal. Pilih per use-case.

---

## Demo: Tiga Engine Berdampingan

- Frontend punya **toggle 3 engine** — menelusuri seluruh perjalanan optimasi:
  - **PostgreSQL** (naif) → `GET /api/products`
  - **PostgreSQL + trigram** → `GET /api/products?engine=trigram`
  - **OpenSearch** → `GET /api/products/_search`
- **Format respons identik** → tinggal tukar URL. Badge **waktu proses**; > 1 dtk → **⚠ lambat**.
- Param **`facet=true/false`** memisahkan biaya *search murni* dari *facet*.
- Halaman **`/admin/products`**: pemilih engine satu klik untuk **banding langsung**.

```mermaid
flowchart LR
    FE[Frontend toggle]
    FE -->|PostgreSQL naif| PG["/api/products<br/>~850 ms ⚠"]
    FE -->|+ trigram| TG["?engine=trigram<br/>~11 ms (selektif)"]
    FE -->|OpenSearch| OS["/api/products/_search<br/>~15 ms + facet murah"]
```

> Narasi: Inilah momen "wow"-nya. Penonton lihat tiga angka berdampingan untuk
> keyword & data yang sama: naif (lambat) → trigram (optimasi DB, jauh lebih cepat)
> → OpenSearch (tercepat + facet nyaris gratis). Perjalanan optimasi jadi kelihatan.

---

## Benchmark: Search Murni (3 Engine)

`processTimeMs` server-side @ **1 juta produk**, rata-rata 5 run (`facet=false`):

| Keyword (match)      |  PG naif | PG+trigram | OpenSearch |
|----------------------|---------:|-----------:|-----------:|
| `titanium` (874)     | 1033 ms  |    13 ms   |    13 ms   |
| `handcrafted` (39rb) |  869 ms  |   235 ms   |    19 ms   |
| `rubber` (80rb)      |  822 ms  |   291 ms   |    24 ms   |
| `ergonomic` (135rb)  | 1118 ms  |   544 ms   |    27 ms   |

> ⚠ **Metodologi**: OpenSearch di-**warmup khusus** (6× semua keyword × mode)
> sebelum diukur — cache dingin OS bisa bikin angka pertama menyesatkan. PG & trigram
> pakai warmup umum 3×. Semua lewat `make`/`bench.py`.

> Narasi: Trigram menjinakkan keyword search (titanium 1033→13 ms), tapi makin umun
> keyword-nya makin kecil untungnya — OpenSearch tetap stabil belasan ms.

---

## Benchmark: + Facet & Ringkasan

Tambahkan facet (`facet=true`) — inilah pembeda sesungguhnya:

| Keyword (match)      |  PG naif | PG+trigram | OpenSearch |
|----------------------|---------:|-----------:|-----------:|
| `titanium` (874)     | 3352 ms  |   357 ms   |    12 ms   |
| `ergonomic` (135rb)  | 3121 ms  |  1759 ms   |    29 ms   |

**Rata-rata lintas keyword:**

| Engine        | Search murni | + Facet | Speedup search vs naif |
|---------------|-------------:|--------:|-----------------------:|
| PG naif       |   960 ms     | 3230 ms | 1×                     |
| PG + trigram  |   271 ms     | 1102 ms | **~3,5×**              |
| OpenSearch    |    21 ms     |   21 ms | **~46×**               |

- **Facet hampir tak menambah biaya di OpenSearch** (21 → 21 ms) — satu pass agregasi.
- Di PG, facet **berlipat**: naif +2,3 dtk, trigram +0,8 dtk → titik mentok optimasi.

> Narasi: Lihat kolom OpenSearch: search & +facet sama-sama ~21 ms. Itulah kenapa
> setelah optimasi DB mentok di facet, CQRS + OpenSearch jadi jawaban.

---

## Hasil & Bukti

- **Parity**: total hasil pencarian PostgreSQL == OpenSearch (terverifikasi untuk
  banyak keyword) → OpenSearch bukan "hasil beda", tapi "hasil sama, jauh lebih cepat".
- **Kecepatan**: keyword + facet @ 1 juta → **~3 dtk (PG)** vs **puluhan ms (OS)**.
- **Skalabilitas**: OpenSearch relatif **datar** terhadap pertambahan data; PostgreSQL
  `LIKE` makin lama makin lambat.

> Narasi: Tekankan parity dulu (biar dipercaya), baru kecepatan. Tanpa parity,
> orang skeptis "ah mungkin hasilnya beda makanya cepat".

---

## Tech Stack Demo

| Komponen          | Teknologi                          | Peran                          |
|-------------------|------------------------------------|--------------------------------|
| product-backend   | Java 25, Spring Boot 4, PostgreSQL | Write model + CRUD             |
| Debezium + Kafka  | Kafka Connect (CDC), Kafka (KRaft) | Sinkronisasi write → read      |
| product-search    | Spring Boot 4, Spring Kafka        | Projector + Search API         |
| OpenSearch        | OpenSearch 3                       | Read model (search + facet)    |
| product-frontend  | SvelteKit + nginx                  | UI + toggle PG/OS              |
| product-faker     | Bun                                | Generator data demo            |

- Semua dijalankan via **Docker/Podman Compose**.

---

## Kapan Pakai CQRS (dan Kapan Tidak)

**Cocok bila:**
- Beban **baca jauh lebih berat**/kompleks daripada tulis (search, analitik, dashboard).
- Butuh model baca yang beda jauh dari model tulis.
- Eventual consistency dapat diterima.

**Hindari/tunda bila:**
- Data kecil & query sederhana (over-engineering).
- Butuh **konsistensi kuat** seketika.
- Tim belum siap dengan kompleksitas operasional (Kafka, CDC, dll).

> Narasi: CQRS itu obat untuk masalah spesifik, bukan default arsitektur. Pakai
> saat memang merasakan sakitnya (seperti demo ini), bukan karena ikut tren.

---

## Ringkasan / Takeaways

1. **Masalah**: `LIKE` + facet di PostgreSQL **tidak skala** (≈3 dtk @ 1 juta).
2. **Optimalkan DB dulu**: `pg_trgm` menurunkan keyword search **850 ms → 11 ms**
   tanpa ubah kode — **jangan lompat ke CQRS sebelum ini dicoba.**
3. **Batas optimasi**: trigram tak menyelesaikan **facet** & beban **OLTP** — di
   sinilah CQRS baru beralasan.
4. **CQRS**: pisahkan write (PostgreSQL) dari read (OpenSearch); jembatan **CDC
   (Debezium → Kafka)** — bersih, tanpa ubah penulis.
5. **Hasil**: hasil **sama**, kecepatan **puluhan ms** (search + facet) vs detik.
6. **Harga**: **eventual consistency** + kompleksitas — pilih sesuai kebutuhan.

> Narasi tutup: "Optimalkan dulu yang ada; pakai CQRS saat memang mentok. Database
> yang tepat untuk pekerjaan yang tepat — tanpa mengorbankan sumber kebenaran."

---

## Terima Kasih / Q&A

- Repo demo: `ProgrammerZamanNow/cqrs-demo`
- Coba sendiri: `podman compose up -d --build` → `make register` → buka `:3000`

> Narasi: Pertanyaan klasik "kenapa tidak pg_trgm saja?" sudah kita **demokan
> langsung**: trigram memang mempercepat keyword (850 ms → 11 ms), tapi facet tetap
> berat (+ratusan ms s/d >1 dtk) dan tetap membebani DB OLTP yang sama. Optimasi DB
> dulu — saat facet & skala mentok, barulah CQRS.
