package com.radusquinho.requestdesk.service;

import com.radusquinho.requestdesk.domain.AuditEvent;
import com.radusquinho.requestdesk.domain.AuditEventRepository;
import com.radusquinho.requestdesk.domain.OutboxMessage;
import com.radusquinho.requestdesk.domain.OutboxMessageRepository;
import com.radusquinho.requestdesk.domain.RequestStatus;
import com.radusquinho.requestdesk.domain.RequestType;
import com.radusquinho.requestdesk.domain.WorkRequest;
import com.radusquinho.requestdesk.domain.WorkRequestRepository;
import com.radusquinho.requestdesk.rules.Decision;
import com.radusquinho.requestdesk.rules.RuleEngine;
import com.radusquinho.requestdesk.rules.RuleProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class RequestService {

    public static final String SYSTEM = "rules";
    private static final Set<RequestStatus> OPEN = Set.of(RequestStatus.PENDING_REVIEW, RequestStatus.ESCALATED);
    private static final Set<RequestStatus> APPROVED = Set.of(RequestStatus.APPROVED, RequestStatus.AUTO_APPROVED);

    private final WorkRequestRepository requests;
    private final AuditEventRepository audit;
    private final OutboxMessageRepository outbox;
    private final RuleEngine engine;
    private final RuleProperties rules;
    private final Clock clock;
    private final JsonMapper json;

    public RequestService(WorkRequestRepository requests, AuditEventRepository audit, OutboxMessageRepository outbox,
                          RuleEngine engine, RuleProperties rules, Clock clock, JsonMapper json) {
        this.requests = requests;
        this.audit = audit;
        this.outbox = outbox;
        this.engine = engine;
        this.rules = rules;
        this.clock = clock;
        this.json = json;
    }

    @Transactional
    public WorkRequest submit(RequestType type, String requester, String title, BigDecimal amount,
                              LocalDate startDate, LocalDate endDate) {
        validateShape(type, amount, startDate, endDate);
        Instant now = clock.instant();
        WorkRequest request = requests.save(new WorkRequest(type, requester, title.strip(), amount, startDate, endDate, now));
        record(request, "SUBMITTED", requester, "Submitted: " + request.getTitle(), now);

        boolean overlaps = type == RequestType.VACATION && overlapsApprovedVacation(request);
        Decision decision = engine.decide(request, LocalDate.now(clock), overlaps);
        request.applyDecision(decision.status(), decision.reason(), now, SYSTEM, now.plus(rules.reviewSla()));
        record(request, "RULE_" + decision.status().name(), SYSTEM, decision.reason(), now);
        return request;
    }

    @Transactional
    public WorkRequest review(long id, boolean approve, String reviewer, boolean reviewerIsAdmin, String comment) {
        WorkRequest request = find(id);
        if (!request.getStatus().isOpen()) {
            throw new IllegalStateException("Request " + id + " is already " + request.getStatus());
        }
        if (request.getStatus() == RequestStatus.ESCALATED && !reviewerIsAdmin) {
            throw new ForbiddenActionException("Escalated requests can only be decided by an admin");
        }
        if (request.getRequester().equals(reviewer)) {
            throw new ForbiddenActionException("You cannot review your own request");
        }
        Instant now = clock.instant();
        RequestStatus status = approve ? RequestStatus.APPROVED : RequestStatus.REJECTED;
        String reason = (approve ? "Approved by " : "Rejected by ") + reviewer
                + (comment == null || comment.isBlank() ? "" : ": " + comment.strip());
        request.applyDecision(status, reason, now, reviewer, null);
        record(request, status.name(), reviewer, reason, now);
        return request;
    }

    /** Called by the SLA job: pending reviews past their deadline go to an admin. */
    @Transactional
    public int escalateOverdue() {
        Instant now = clock.instant();
        List<WorkRequest> overdue = requests.findByStatusAndReviewDueAtBefore(RequestStatus.PENDING_REVIEW, now);
        for (WorkRequest request : overdue) {
            String reason = "No decision within " + rules.reviewSla().toHours() + " h: escalated to an admin";
            request.applyDecision(RequestStatus.ESCALATED, reason, now, SYSTEM, now.plus(rules.reviewSla()));
            record(request, "SLA_ESCALATED", SYSTEM, reason, now);
        }
        return overdue.size();
    }

    @Transactional(readOnly = true)
    public List<WorkRequest> mine(String requester) {
        return requests.findByRequesterOrderByCreatedAtDesc(requester);
    }

    @Transactional(readOnly = true)
    public List<WorkRequest> queue(boolean includeEscalated) {
        Set<RequestStatus> statuses = includeEscalated ? OPEN : Set.of(RequestStatus.PENDING_REVIEW);
        return requests.findByStatusInOrderByCreatedAtAsc(statuses);
    }

    @Transactional(readOnly = true)
    public WorkRequest find(long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException("Request " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> history(long id) {
        find(id);
        return audit.findByRequestIdOrderByAtAscIdAsc(id);
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recentAudit() {
        return audit.findTop100ByOrderByAtDescIdDesc();
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> webhooks(long id) {
        find(id);
        return outbox.findByRequestIdOrderByIdAsc(id);
    }

    private boolean overlapsApprovedVacation(WorkRequest candidate) {
        return requests.findByTypeAndStatusIn(RequestType.VACATION, APPROVED).stream()
                .filter(other -> !other.getId().equals(candidate.getId()))
                .anyMatch(other -> candidate.overlaps(other.getStartDate(), other.getEndDate()));
    }

    /** Every status change is audited and published, in the same transaction. */
    private void record(WorkRequest request, String action, String actor, String detail, Instant at) {
        audit.save(new AuditEvent(request.getId(), action, actor, detail, at));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", action);
        payload.put("requestId", request.getId());
        payload.put("type", request.getType().name());
        payload.put("status", request.getStatus().name());
        payload.put("requester", request.getRequester());
        payload.put("actor", actor);
        payload.put("detail", detail);
        payload.put("at", at.toString());
        outbox.save(new OutboxMessage(action, request.getId(), json.writeValueAsString(payload), at));
    }

    private static void validateShape(RequestType type, BigDecimal amount, LocalDate start, LocalDate end) {
        if (type == RequestType.VACATION) {
            if (start == null || end == null) {
                throw new IllegalArgumentException("A vacation needs a start and an end date");
            }
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("The end date is before the start date");
            }
        } else if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("An expense or purchase needs a positive amount");
        }
    }
}
