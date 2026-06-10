-- N-gram (trigram) search untuk PostgreSQL — pembanding "adil" terhadap OpenSearch.
--
-- pg_trgm memecah teks jadi trigram (n-gram 3 karakter) dan menyimpannya di GIN
-- index, konsep yang sama dengan analyzer n-gram di OpenSearch. Dengan index ini,
-- query `LOWER(name) LIKE '%kata%'` yang SUDAH ADA bisa memakai index alih-alih
-- sequential scan — tanpa mengubah satu baris pun SQL aplikasi.
--
-- CATATAN DEMO: setelah index ini ada, endpoint naif (`?engine=naive`, default)
-- sengaja menonaktifkan bitmap scan per-transaksi (SET LOCAL enable_bitmapscan=off)
-- agar tetap memperagakan kondisi "tanpa trigram" (sequential scan). Endpoint
-- `?engine=trigram` membiarkan planner memakai GIN index di bawah ini.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- GIN trigram pada name & description (lower-case agar cocok pencarian case-insensitive).
-- Build atas data besar bisa makan waktu sekali jalan (terutama description).
CREATE INDEX idx_products_name_trgm ON products USING GIN (LOWER(name) gin_trgm_ops);
CREATE INDEX idx_products_description_trgm ON products USING GIN (LOWER(description) gin_trgm_ops);
