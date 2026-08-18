
-- ============================================================
-- ORDERS LAB DATABASE SCHEMA
-- ============================================================

-- ------------------------------------------------------------
-- UI USERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL
);

INSERT INTO app_users (username, password)
VALUES ('admin', 'admin123')
ON CONFLICT (username)
DO UPDATE SET password = EXCLUDED.password;


-- ------------------------------------------------------------
-- INVENTORY
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory (
    sku VARCHAR(50) PRIMARY KEY,
    item_name VARCHAR(255) NOT NULL,
    category VARCHAR(100),
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER NOT NULL DEFAULT 10,
    unit_price NUMERIC(12,2) NOT NULL,
    warehouse VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ------------------------------------------------------------
-- SALES
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sales (
    id SERIAL PRIMARY KEY,
    customer_name VARCHAR(255) NOT NULL,
    payment_method VARCHAR(50),
    total_amount NUMERIC(12,2) NOT NULL,
    sale_status VARCHAR(50),
    stripe_session_id VARCHAR(255) UNIQUE,
    stripe_payment_intent VARCHAR(255),
    discount_amount NUMERIC(12,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ------------------------------------------------------------
-- ORDERS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
    id SERIAL PRIMARY KEY,
    sale_id INTEGER REFERENCES sales(id),
    customer_name VARCHAR(255) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ------------------------------------------------------------
-- PAYMENTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id SERIAL PRIMARY KEY,
    sale_id INTEGER REFERENCES sales(id),
    order_id INTEGER REFERENCES orders(id),
    payment_method VARCHAR(50),
    amount NUMERIC(12,2) NOT NULL,
    payment_status VARCHAR(50),
    transaction_ref VARCHAR(255),
    stripe_session_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ------------------------------------------------------------
-- SALE ITEMS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sale_items (
    id SERIAL PRIMARY KEY,
    sale_id INTEGER NOT NULL REFERENCES sales(id),
    sku VARCHAR(50),
    item_name VARCHAR(255),
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    line_total NUMERIC(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ------------------------------------------------------------
-- CHECKOUT ATTEMPTS
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS checkout_attempts (
    id SERIAL PRIMARY KEY,
    customer_name VARCHAR(255),
    stripe_session_id VARCHAR(255),
    checkout_status VARCHAR(50),
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- ------------------------------------------------------------
-- LAB INVENTORY DATA
-- ------------------------------------------------------------
INSERT INTO inventory
    (sku, item_name, category, stock_quantity, reorder_level, unit_price, warehouse)
VALUES
    ('LAPTOP-001', 'Business Laptop', 'Computers', 100, 20, 1299.99, 'ATL-01'),
    ('MONITOR-001', '27 Inch Monitor', 'Displays', 150, 25, 349.99, 'ATL-01'),
    ('KEYBOARD-001', 'Mechanical Keyboard', 'Accessories', 250, 30, 89.99, 'ATL-02'),
    ('MOUSE-001', 'Wireless Mouse', 'Accessories', 300, 40, 49.99, 'ATL-02'),
    ('HEADSET-001', 'USB Headset', 'Accessories', 80, 15, 119.99, 'ATL-01'),
    ('DOCK-001', 'USB-C Dock', 'Accessories', 60, 15, 199.99, 'ATL-02')
ON CONFLICT (sku) DO NOTHING;


-- ------------------------------------------------------------
-- OWNERSHIP
-- ------------------------------------------------------------
ALTER TABLE app_users OWNER TO orders_user;
ALTER TABLE inventory OWNER TO orders_user;
ALTER TABLE sales OWNER TO orders_user;
ALTER TABLE orders OWNER TO orders_user;
ALTER TABLE payments OWNER TO orders_user;
ALTER TABLE sale_items OWNER TO orders_user;
ALTER TABLE checkout_attempts OWNER TO orders_user;

