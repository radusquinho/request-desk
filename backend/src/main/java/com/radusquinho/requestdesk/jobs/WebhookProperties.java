package com.radusquinho.requestdesk.jobs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "desk.webhook")
public record WebhookProperties(String url, int maxAttempts, String secret) {

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
