package com.radusquinho.requestdesk.jobs;

import com.radusquinho.requestdesk.domain.OutboxMessage;
import com.radusquinho.requestdesk.domain.OutboxMessageRepository;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

/**
 * Delivers outbox events to the configured webhook URL. Each message carries its outbox id
 * as an Idempotency-Key header, so a receiver can safely ignore a repeated delivery.
 * Failures retry with exponential backoff and give up after max-attempts.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);

    private final OutboxMessageRepository outbox;
    private final WebhookProperties props;
    private final Clock clock;
    private final RestClient http;

    public WebhookDispatcher(OutboxMessageRepository outbox, WebhookProperties props, Clock clock) {
        this.outbox = outbox;
        this.props = props;
        this.clock = clock;
        // HTTP/1.1 on purpose: the JDK client otherwise tries an h2c upgrade on plain http://,
        // which many webhook receivers (uvicorn, for one) reject with 400.
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Scheduled(fixedDelayString = "${desk.jobs.webhook-dispatch}")
    @Transactional
    public void dispatch() {
        if (!props.enabled()) {
            return; // events stay visible in the outbox, nothing is sent
        }
        Instant now = clock.instant();
        List<OutboxMessage> due = outbox.findTop20ByStateAndNextAttemptAtLessThanEqualOrderByIdAsc(
                OutboxMessage.State.PENDING, now);
        for (OutboxMessage message : due) {
            try {
                http.post()
                        .uri(props.url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "request-desk-" + message.getId())
                        .header("X-Event-Type", message.getEventType())
                        .header("X-Signature", WebhookSignature.sign(message.getPayload(), props.secret()))
                        .body(message.getPayload())
                        .retrieve()
                        .toBodilessEntity();
                message.markDelivered(now);
            } catch (RuntimeException e) {
                message.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage(), now, props.maxAttempts());
                log.warn("Webhook {} failed (attempt {}): {}", message.getId(), message.getAttempts(), e.getMessage());
            }
        }
    }
}
