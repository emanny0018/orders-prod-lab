package com.manny.orders;

import redis.clients.jedis.Jedis;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductCatalog {
    private static final String CACHE_KEY = "catalog:products:v1";
    private static final int TTL_SECONDS = 30;

    public static class Product {
        public String sku;
        public String itemName;
        public String category;
        public int stockQuantity;
        public String unitPrice;

        public Product(String sku, String itemName, String category, int stockQuantity, String unitPrice) {
            this.sku = sku;
            this.itemName = itemName;
            this.category = category;
            this.stockQuantity = stockQuantity;
            this.unitPrice = unitPrice;
        }

        public String label() {
            return category + " - " + itemName + " (" + sku + ") | Stock: " + stockQuantity + " | $" + unitPrice;
        }

        public String toCacheLine() {
            return esc(sku) + "|" + esc(itemName) + "|" + esc(category) + "|" + stockQuantity + "|" + unitPrice;
        }

        public static Product fromCacheLine(String line) {
            String[] p = line.split("\\|", -1);
            return new Product(unesc(p[0]), unesc(p[1]), unesc(p[2]), Integer.parseInt(p[3]), p[4]);
        }

        private static String esc(String v) {
            if (v == null) return "";
            return v.replace("\\", "\\\\").replace("|", "\\p");
        }

        private static String unesc(String v) {
            return v.replace("\\p", "|").replace("\\\\", "\\");
        }
    }

    public static List<Product> getProducts(Connection conn) throws Exception {
        try (Jedis jedis = Cache.getClient()) {
            String cached = jedis.get(CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                List<Product> products = new ArrayList<>();
                for (String line : cached.split("\n")) {
                    if (!line.isBlank()) products.add(Product.fromCacheLine(line));
                }
                return products;
            }
        } catch (Exception e) {
            System.err.println("CATALOG_CACHE_READ_FAILED: " + e.getMessage());
        }

        List<Product> products = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT sku, item_name, category, stock_quantity, unit_price FROM inventory ORDER BY category, item_name");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(new Product(
                        rs.getString("sku"),
                        rs.getString("item_name"),
                        rs.getString("category"),
                        rs.getInt("stock_quantity"),
                        rs.getBigDecimal("unit_price").toString()
                ));
            }
        }

        try (Jedis jedis = Cache.getClient()) {
            StringBuilder payload = new StringBuilder();
            for (Product product : products) {
                payload.append(product.toCacheLine()).append("\n");
            }
            jedis.setex(CACHE_KEY, TTL_SECONDS, payload.toString());
        } catch (Exception e) {
            System.err.println("CATALOG_CACHE_WRITE_FAILED: " + e.getMessage());
        }

        return products;
    }

    public static void invalidate() {
        try (Jedis jedis = Cache.getClient()) {
            jedis.del(CACHE_KEY);
        } catch (Exception e) {
            System.err.println("CATALOG_CACHE_INVALIDATE_FAILED: " + e.getMessage());
        }
    }
}
