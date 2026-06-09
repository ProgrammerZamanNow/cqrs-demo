# product-frontend

Halaman pencarian produk (homepage ala e-commerce) — search bar di atas, panel
**facet** di kiri, grid produk di kanan. Punya **toggle PostgreSQL ⇄ OpenSearch**
untuk membandingkan performa kedua read source secara langsung.

Bagian dari [CQRS Demo](../README.md).

---

## Tech
- **SvelteKit** (Svelte 5, runes) + **TypeScript**, dijalankan/di-build dengan **Bun**.
- Build **static** (`@sveltejs/adapter-static`, SPA) → disajikan **nginx** di container.
- Tanpa state server; semua data via `fetch` ke `/api/...`.

---

## Toggle PostgreSQL vs OpenSearch
Combo box di kiri search bar memilih sumber query:
| Pilihan      | Endpoint                  | Dilayani         |
|--------------|---------------------------|------------------|
| `PostgreSQL` | `/api/products`           | product-backend  |
| `OpenSearch` | `/api/products/_search`   | product-search   |

Karena envelope respons identik, frontend cukup menukar URL dan menampilkan
`metadata.processTimeMs` (badge kanan atas; berubah **⚠ lambat** bila > 1 detik).

Routing `/api` ditangani:
- **dev**: Vite proxy (`vite.config.ts`) → `BACKEND_URL` (8080) & `SEARCH_URL` (8081).
- **produksi**: nginx (`nginx.conf`) → `product-backend` & `product-search`.

---

## Menjalankan

### Dev (butuh backend & search jalan)
```bash
bun install
bun run dev          # http://localhost:5173
```
Override target proxy bila perlu: `BACKEND_URL=... SEARCH_URL=... bun run dev`.

### Build static
```bash
bun run build        # output ke ./build (SPA)
bun run preview      # pratinjau hasil build
```

### Container (nginx) — biasanya via compose root
```bash
# dari root cqrs-demo:
podman compose up -d --build product-frontend   # http://localhost:3000
```

---

## Pengembangan
- `bun run check` — type-check (svelte-check).
- Halaman utama: [`src/routes/+page.svelte`](src/routes/+page.svelte).
- `SIZE` (item per halaman) dan daftar `SORTS` ada di atas file tersebut.
