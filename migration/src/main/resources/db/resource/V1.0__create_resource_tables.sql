-- V1.0__create_resource_tables.sql (rbac_resource database)

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(15, 2) NOT NULL,
    stock       INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE orders (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(255) NOT NULL,
    status      VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    total       NUMERIC(15, 2) NOT NULL DEFAULT 0,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products(id),
    quantity    INT NOT NULL DEFAULT 1,
    unit_price  NUMERIC(15, 2) NOT NULL
);

CREATE INDEX idx_orders_username   ON orders(username);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- Seed data
INSERT INTO products (name, description, price, stock) VALUES
    ('Product A', 'Description for Product A', 100.00, 50),
    ('Product B', 'Description for Product B', 200.00, 30),
    ('Product C', 'Description for Product C', 350.00, 20);
