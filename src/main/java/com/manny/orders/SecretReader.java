package com.manny.orders;

import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

public class SecretReader {

    public static String getStripeSecretKey() throws Exception {
        return getSecret("stripe.secret");
    }

    public static String getStripeWebhookSecret() throws Exception {
        return getSecret("stripe.webhook.secret");
    }

    private static String getSecret(String alias) throws Exception {
        String path = getenvRequired("STRIPE_KEYSTORE_PATH");
        String type = getenvRequired("STRIPE_KEYSTORE_TYPE");
        String password = getenvRequired("STRIPE_KEYSTORE_PASSWORD");

        KeyStore keyStore = KeyStore.getInstance(type);

        try (FileInputStream fis = new FileInputStream(path)) {
            keyStore.load(fis, password.toCharArray());
        }

        KeyStore.PasswordProtection protection =
                new KeyStore.PasswordProtection(password.toCharArray());

        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(alias, protection);

        if (entry == null) {
            throw new IllegalStateException("Secret alias not found: " + alias);
        }

        SecretKey secretKey = entry.getSecretKey();
        return new String(secretKey.getEncoded(), StandardCharsets.UTF_8);
    }

    private static String getenvRequired(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
