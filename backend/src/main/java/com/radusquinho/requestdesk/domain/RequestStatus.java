package com.radusquinho.requestdesk.domain;

public enum RequestStatus {
    /** Approved by a rule, no human involved. */
    AUTO_APPROVED,
    /** Rejected by a rule, no human involved. */
    AUTO_REJECTED,
    /** Waiting for a manager. */
    PENDING_REVIEW,
    /** Sent to an admin, either by a rule or because the review SLA expired. */
    ESCALATED,
    APPROVED,
    REJECTED;

    public boolean isOpen() {
        return this == PENDING_REVIEW || this == ESCALATED;
    }
}
