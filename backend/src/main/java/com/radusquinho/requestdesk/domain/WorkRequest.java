package com.radusquinho.requestdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "work_request")
public class WorkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestType type;

    @Column(nullable = false)
    private String requester;

    @Column(nullable = false)
    private String title;

    private BigDecimal amount;

    private LocalDate startDate;

    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant reviewDueAt;

    private Instant decidedAt;

    private String decidedBy;

    protected WorkRequest() {
        // for JPA
    }

    public WorkRequest(RequestType type, String requester, String title, BigDecimal amount,
                       LocalDate startDate, LocalDate endDate, Instant createdAt) {
        this.type = type;
        this.requester = requester;
        this.title = title;
        this.amount = amount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
        this.status = RequestStatus.PENDING_REVIEW;
        this.reason = "Submitted";
    }

    /** Inclusive number of calendar days for a vacation, or 0 when dates are missing. */
    public long days() {
        if (startDate == null || endDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        return startDate != null && endDate != null
                && !startDate.isAfter(otherEnd) && !endDate.isBefore(otherStart);
    }

    public void applyDecision(RequestStatus newStatus, String newReason, Instant at, String actor, Instant dueAt) {
        this.status = newStatus;
        this.reason = newReason;
        this.reviewDueAt = newStatus.isOpen() ? dueAt : null;
        if (!newStatus.isOpen()) {
            this.decidedAt = at;
            this.decidedBy = actor;
        }
    }

    public Long getId() { return id; }
    public RequestType getType() { return type; }
    public String getRequester() { return requester; }
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public RequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewDueAt() { return reviewDueAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecidedBy() { return decidedBy; }
}
