package com.manny.orders;

import java.io.Serializable;
import java.math.BigDecimal;

public class CartItem implements Serializable {
    public String sku;
    public String itemName;
    public int quantity;
    public BigDecimal unitPrice;

    public CartItem(String sku, String itemName, int quantity, BigDecimal unitPrice) {
        this.sku = sku;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public BigDecimal total() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
