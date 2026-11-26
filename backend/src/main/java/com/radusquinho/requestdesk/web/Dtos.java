package com.radusquinho.requestdesk.web;

import com.radusquinho.requestdesk.domain.AuditEvent;
import com.radusquinho.requestdesk.domain.OutboxMessage;
import com.radusquinho.requestdesk.domain.RequestType;
import com.radusquinho.requestdesk.domain.WorkRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes of the API, kept apart from the JPA entities. */
public final class Dtos {

    private Dtos() {
    }

    public record NewRequest(
            @NotNull RequestType type,
            @NotBlank @Size(max = 200) String title,
            @DecimalMax("1000000") BigDecimal amount,
            LocalDate startDate,
            LocalDate endDate) {
    }

    public record ReviewCommand(@Size(max = 300) String comment) {
    }

    public record RequestView(Long id, String type, String requester, String title, BigDecimal amount,
                              LocalDate startDate, LocalDate endDate, String status, String reason,
                              Instant createdAt, Instant reviewDueAt, Instant decidedAt, String decidedBy) {

        static RequestView of(WorkRequest r) {
            return new RequestView(r.getId(), r.getType().name(), r.getRequester(), r.getTitle(), r.getAmount(),
                    r.getStartDate(), r.getEndDate(), r.getStatus().name(), r.getReason(), r.getCreatedAt(),
                    r.getReviewDueAt(), r.getDecidedAt(), r.getDecidedBy());
        }
    }

    public record AuditView(Long id, Long requestId, String action, String actor, String detail, Instant at) {

        static AuditView of(AuditEvent e) {
            return new AuditView(e.getId(), e.getRequestId(), e.getAction(), e.getActor(), e.getDetail(), e.getAt());
        }
    }

    public record WebhookView(Long id, String eventType, String state, int attempts, Instant deliveredAt,
                              String lastError) {

        static WebhookView of(OutboxMessage m) {
            return new WebhookView(m.getId(), m.getEventType(), m.getState().name(), m.getAttempts(),
                    m.getDeliveredAt(), m.getLastError());
        }
    }

    public record HistoryView(RequestView request, List<AuditView> audit, List<WebhookView> webhooks) {
    }

    public record MeView(String username, List<String> roles) {
    }

    public record ErrorView(String error) {
    }
}
