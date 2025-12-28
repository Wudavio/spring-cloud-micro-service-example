-- 微服務資料庫初始化腳本
-- 請以 PostgreSQL 超級用戶身份執行此腳本

-- 創建產品服務資料庫和用戶
CREATE DATABASE product_db;
CREATE USER product_user WITH PASSWORD 'product_pass';
GRANT ALL PRIVILEGES ON DATABASE product_db TO product_user;

-- 創建庫存服務資料庫和用戶
CREATE DATABASE inventory_db;
CREATE USER inventory_user WITH PASSWORD 'inventory_pass';
GRANT ALL PRIVILEGES ON DATABASE inventory_db TO inventory_user;

-- 創建訂單服務資料庫和用戶
CREATE DATABASE order_db;
CREATE USER order_user WITH PASSWORD 'order_pass';
GRANT ALL PRIVILEGES ON DATABASE order_db TO order_user;

-- 為每個用戶授予在其對應數據庫中的 public schema 權限
\c product_db;
GRANT ALL ON SCHEMA public TO product_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO product_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO product_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO product_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO product_user;

\c inventory_db;
GRANT ALL ON SCHEMA public TO inventory_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO inventory_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO inventory_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO inventory_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO inventory_user;

\c order_db;
GRANT ALL ON SCHEMA public TO order_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO order_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO order_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO order_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO order_user;

-- 回到主數據庫顯示創建結果
\c postgres;
\l
\du