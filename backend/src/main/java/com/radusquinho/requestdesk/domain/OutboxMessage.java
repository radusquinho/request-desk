package com.radusquinho.requestdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * A webhook event waiting to be delivered. Written in the same transaction as the
 * status change, so an event is never lost and never sent for a change that rolled back.
 */
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    public enum State { PENDING, DELIVERED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant deliveredAt;

    private String lastError;

    protected OutboxMessage() {
        // for JPA
    }

    public OutboxMessage(String eventType, Long requestId, String payload, Instant now) {
        this.eventType = eventType;
        this.requestId = requestId;
        this.payload = payload;
        this.state = State.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
    }

    public void markDelivered(Instant at) {
        this.attempts++;
        this.state = State.DELIVERED;
        this.deliveredAt = at;
        this.lastError = null;
    }

    /** Exponential backoff: 1, 2, 4, 8... minutes, then FAILED after maxAttempts. */
    public void markFailed(String error, Instant now, int maxAttempts) {
        this.attempts++;
        this.lastError = error.length() > 500 ? error.substring(0, 500) : error;
        if (attempts >= maxAttempts) {
            this.state = State.FAILED;
        } else {
            this.nextAttemptAt = now.plus(Duration.ofMinutes(1L << (attempts - 1)));
        }
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public Long getRequestId() { return requestId; }
    public String getPayload() { return payload; }
    public State getState() { return state; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public String getLastError() { return lastError; }
}
