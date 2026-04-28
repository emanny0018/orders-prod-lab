package com.manny.orders;

import com.rabbitmq.client.*;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class InventoryEventConsumer {

    public static void main(String[] args) throws Exception {
        ConnectionFactory factory = QueuePublisher.factory();

        com.rabbitmq.client.Connection rabbitConnection = factory.newConnection();
        Channel channel = rabbitConnection.createChannel();

        channel.queueDeclare(QueuePublisher.QUEUE_NAME, true, false, false, null);
        channel.basicQos(1);

        System.out.println("InventoryEventConsumer started. Waiting for ORDER_CREATED events...");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String body = new String(delivery.getBody(), StandardCharsets.UTF_8);

            try {
                JSONObject event = new JSONObject(body);

                String eventType = event.getString("event");

                if (!"ORDER_CREATED".equals(eventType)) {
                    System.out.println("Ignoring event: " + body);
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    return;
                }

                int orderId = event.getInt("orderId");
                String sku = event.getString("sku");
                int quantity = event.getInt("quantity");

                try (Connection db = Db.getConnection();
                     PreparedStatement ps = db.prepareStatement(
                             "UPDATE inventory " +
                             "SET stock_quantity = stock_quantity - ?, updated_at = CURRENT_TIMESTAMP " +
                             "WHERE sku = ? AND stock_quantity >= ?")) {

                    ps.setInt(1, quantity);
                    ps.setString(2, sku);
                    ps.setInt(3, quantity);

                    int updated = ps.executeUpdate();

                    if (updated == 1) {
                        System.out.println("INVENTORY_UPDATED orderId=" + orderId + " sku=" + sku + " qty=" + quantity);
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    } else {
                        System.err.println("INVENTORY_UPDATE_FAILED orderId=" + orderId + " sku=" + sku + " qty=" + quantity + " reason=SKU_NOT_FOUND_OR_INSUFFICIENT_STOCK");
                        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
                    }
                }

            } catch (Exception e) {
                System.err.println("CONSUMER_PROCESSING_FAILED body=" + body + " error=" + e.getMessage());
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        channel.basicConsume(QueuePublisher.QUEUE_NAME, false, deliverCallback, consumerTag -> {});
    }
}
