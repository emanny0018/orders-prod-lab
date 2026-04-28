package com.manny.orders;

public class DashboardSummary {
    public int totalOrders;
    public int paidOrders;
    public int pendingOrders;
    public int failedOrders;
    public String revenue;

    public DashboardSummary(int totalOrders, int paidOrders, int pendingOrders, int failedOrders, String revenue) {
        this.totalOrders = totalOrders;
        this.paidOrders = paidOrders;
        this.pendingOrders = pendingOrders;
        this.failedOrders = failedOrders;
        this.revenue = revenue;
    }

    public String toCacheValue() {
        return totalOrders + "|" + paidOrders + "|" + pendingOrders + "|" + failedOrders + "|" + revenue;
    }

    public static DashboardSummary fromCacheValue(String value) {
        String[] parts = value.split("\\|");
        return new DashboardSummary(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                Integer.parseInt(parts[3]),
                parts[4]
        );
    }
}
