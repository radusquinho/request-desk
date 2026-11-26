package com.radusquinho.requestdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false)
    private String detail;

    @Column(nullable = false)
    private Instant at;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(Long requestId, String action, String actor, String detail, Instant at) {
        this.requestId = requestId;
        this.action = action;
        this.actor = actor;
        this.detail = detail;
        this.at = at;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getDetail() { return detail; }
    public Instant getAt() { return at; }
}
