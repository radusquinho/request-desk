package com.radusquinho.requestdesk.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.radusquinho.requestdesk.domain.OutboxMessage;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxBackoffTest {

    private static final Instant T0 = Instant.parse("2026-11-26T09:00:00Z");

    @Test
    void retriesBackOffExponentiallyThenGiveUp() {
        OutboxMessage message = new OutboxMessage("SUBMITTED", 1L, "{}", T0);

        message.markFailed("timeout", T0, 3);
        assertThat(message.getState()).isEqualTo(OutboxMessage.State.PENDING);
        assertThat(message.getNextAttemptAt()).isEqualTo(T0.plusSeconds(60));

        message.markFailed("timeout", T0, 3);
        assertThat(message.getNextAttemptAt()).isEqualTo(T0.plusSeconds(120));

        message.markFailed("timeout", T0, 3);
        assertThat(message.getState()).isEqualTo(OutboxMessage.State.FAILED);
        assertThat(message.getAttempts()).isEqualTo(3);
    }

    @Test
    void deliveryClearsTheLastError() {
        OutboxMessage message = new OutboxMessage("SUBMITTED", 1L, "{}", T0);
        message.markFailed("503", T0, 5);
        message.markDelivered(T0.plusSeconds(60));
        assertThat(message.getState()).isEqualTo(OutboxMessage.State.DELIVERED);
        assertThat(message.getLastError()).isNull();
        assertThat(message.getAttempts()).isEqualTo(2);
    }
}
