package com.manny.orders;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.nio.charset.StandardCharsets;

public class QueuePublisher {
    public static final String QUEUE_NAME = "orders.events";

    public static ConnectionFactory factory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        return factory;
    }

    public static void publishOrderEvent(String message) throws Exception {
        try (Connection connection = factory().newConnection();
             Channel channel = connection.createChannel()) {

            channel.queueDeclare(QUEUE_NAME, true, false, false, null);

            channel.basicPublish(
                    "",
                    QUEUE_NAME,
                    null,
                    message.getBytes(StandardCharsets.UTF_8)
            );
        }
    }

    public static void testConnection() throws Exception {
        try (Connection connection = factory().newConnection();
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        }
    }
}
