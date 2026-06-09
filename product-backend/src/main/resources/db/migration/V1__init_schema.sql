-- Skema awal: categories, brands, products (REQUIREMENT bagian 5)
-- Primary key memakai UUID, di-generate oleh aplikasi.

CREATE TABLE categories
(
    id          UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name)
);

CREATE TABLE brands
(
    id          UUID         NOT NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_brands PRIMARY KEY (id),
    CONSTRAINT uk_brands_name UNIQUE (name)
);

CREATE TABLE products
(
    id          UUID           NOT NULL,
    sku         VARCHAR(50)    NOT NULL,
    name        VARCHAR(150)   NOT NULL,
    description TEXT,
    price       NUMERIC(15, 2) NOT NULL,
    stock       INTEGER        NOT NULL DEFAULT 0,
    image_url   VARCHAR(500),
    category_id UUID           NOT NULL,
    brand_id    UUID           NOT NULL,
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL,
    CONSTRAINT pk_products PRIMARY KEY (id),
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
);

-- Index pendukung filter & facet (REQUIREMENT 6.3.2)
CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_brand ON products (brand_id);
CREATE INDEX idx_products_price ON products (price);
CREATE INDEX idx_products_stock ON products (stock);
-- Keyword search case-insensitive pada name
CREATE INDEX idx_products_name_lower ON products (LOWER(name));
