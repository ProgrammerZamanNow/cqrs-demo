import { faker } from "@faker-js/faker";

/**
 * Generator data dummy untuk product-backend.
 * Membuat brand, category, lalu product via REST API.
 *
 * Konfigurasi via environment variable (semua opsional):
 *   BASE_URL        default http://localhost:8080
 *   BRAND_COUNT     default 100
 *   CATEGORY_COUNT  default 50
 *   PRODUCT_COUNT   default 100000
 *   CONCURRENCY     default 64     (jumlah request paralel saat membuat product)
 *   DRY_RUN         "1" untuk tidak benar-benar memanggil API (uji logika saja)
 */

const BASE_URL = process.env.BASE_URL ?? "http://localhost:8080";
const BRAND_COUNT = Number(process.env.BRAND_COUNT ?? 100);
const CATEGORY_COUNT = Number(process.env.CATEGORY_COUNT ?? 50);
const PRODUCT_COUNT = Number(process.env.PRODUCT_COUNT ?? 100_000);
const CONCURRENCY = Number(process.env.CONCURRENCY ?? 64);
const DRY_RUN = process.env.DRY_RUN === "1" || process.env.DRY_RUN === "true";

interface Created {
  id: string;
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));
const pick = <T>(arr: T[]): T => arr[Math.floor(Math.random() * arr.length)]!;

/** POST JSON ke product-backend, mengembalikan field `data`. */
async function post<T>(path: string, body: unknown): Promise<T> {
  if (DRY_RUN) {
    return { id: crypto.randomUUID() } as T;
  }
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`POST ${path} -> ${res.status}: ${text}`);
  }
  const json = (await res.json()) as { data: T };
  return json.data;
}

/** POST dengan retry sederhana untuk error transien. */
async function postRetry<T>(path: string, body: unknown, attempts = 3): Promise<T> {
  let lastErr: unknown;
  for (let a = 1; a <= attempts; a++) {
    try {
      return await post<T>(path, body);
    } catch (err) {
      lastErr = err;
      await sleep(100 * a);
    }
  }
  throw lastErr;
}

/** Menjalankan `task` sebanyak `total` kali dengan `concurrency` worker paralel. */
async function pool(
  total: number,
  concurrency: number,
  task: (i: number) => Promise<void>,
  onProgress?: (done: number) => void,
): Promise<number> {
  let next = 0;
  let done = 0;
  let failed = 0;

  async function worker() {
    while (true) {
      const i = next++;
      if (i >= total) break;
      try {
        await task(i);
      } catch (err) {
        failed++;
        if (failed <= 10) console.error(`  ! gagal index ${i}:`, (err as Error).message);
      }
      done++;
      if (onProgress && done % 5000 === 0) onProgress(done);
    }
  }

  await Promise.all(Array.from({ length: Math.min(concurrency, total) }, worker));
  if (onProgress) onProgress(done);
  return failed;
}

function clamp100(value: string): string {
  return value.length > 100 ? value.slice(0, 100) : value;
}

async function main() {
  console.log("=== product-faker ===");
  console.log(`Target   : ${BASE_URL}`);
  console.log(`Brands   : ${BRAND_COUNT}`);
  console.log(`Categories: ${CATEGORY_COUNT}`);
  console.log(`Products : ${PRODUCT_COUNT}`);
  console.log(`Concurrency: ${CONCURRENCY}${DRY_RUN ? "  (DRY_RUN)" : ""}`);
  console.log("");

  const started = Date.now();

  // --- Brands ---
  console.log(`Membuat ${BRAND_COUNT} brand...`);
  const brandIds: string[] = new Array(BRAND_COUNT);
  await pool(BRAND_COUNT, 16, async (i) => {
    const brand = await postRetry<Created>("/api/brands", {
      name: clamp100(`${faker.company.name()} #${i + 1}`),
      description: faker.company.catchPhrase(),
    });
    brandIds[i] = brand.id;
  });
  const brands = brandIds.filter(Boolean);
  console.log(`  -> ${brands.length} brand dibuat`);

  // --- Categories ---
  console.log(`Membuat ${CATEGORY_COUNT} category...`);
  const categoryIds: string[] = new Array(CATEGORY_COUNT);
  await pool(CATEGORY_COUNT, 16, async (i) => {
    const category = await postRetry<Created>("/api/categories", {
      name: clamp100(`${faker.commerce.department()} ${i + 1}`),
      description: faker.lorem.sentence(),
    });
    categoryIds[i] = category.id;
  });
  const categories = categoryIds.filter(Boolean);
  console.log(`  -> ${categories.length} category dibuat`);

  if (brands.length === 0 || categories.length === 0) {
    throw new Error("Brand/category gagal dibuat — produk tidak bisa di-generate.");
  }

  // --- Products ---
  console.log(`Membuat ${PRODUCT_COUNT} product...`);
  const failed = await pool(
    PRODUCT_COUNT,
    CONCURRENCY,
    async (i) => {
      const priceRupiah = faker.number.int({ min: 10_000, max: 5_000_000 });
      await postRetry("/api/products", {
        sku: `SKU-${String(i + 1).padStart(7, "0")}`,
        name: faker.commerce.productName(),
        description: faker.commerce.productDescription(),
        price: priceRupiah,
        stock: faker.number.int({ min: 0, max: 500 }),
        categoryId: pick(categories),
        brandId: pick(brands),
      });
    },
    (done) => console.log(`  products: ${done}/${PRODUCT_COUNT}`),
  );

  const elapsed = ((Date.now() - started) / 1000).toFixed(1);
  console.log("");
  console.log("=== Selesai ===");
  console.log(`Brands    : ${brands.length}`);
  console.log(`Categories: ${categories.length}`);
  console.log(`Products  : ${PRODUCT_COUNT - failed} sukses, ${failed} gagal`);
  console.log(`Durasi    : ${elapsed}s`);
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});
