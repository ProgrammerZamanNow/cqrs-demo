-- Persiapan CDC (Debezium): full row image untuk UPDATE/DELETE + publication.
-- wal_level=logical diset di level server (lihat docker-compose postgres command).

ALTER TABLE categories REPLICA IDENTITY FULL;
ALTER TABLE brands REPLICA IDENTITY FULL;
ALTER TABLE products REPLICA IDENTITY FULL;

CREATE PUBLICATION dbz_publication FOR TABLE categories, brands, products;
