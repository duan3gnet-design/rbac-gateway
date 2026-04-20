DELETE FROM order_items;
DELETE FROM orders;
DELETE FROM products;

INSERT INTO products (id, name, description, price, stock) VALUES
    (1, 'Product A', 'Description for Product A', 100.00, 50),
    (2, 'Product B', 'Description for Product B', 200.00, 30),
    (3, 'Product C', 'Description for Product C', 350.00, 20);
