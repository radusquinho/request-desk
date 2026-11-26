package com.radusquinho.requestdesk.rules;

import com.radusquinho.requestdesk.domain.RequestStatus;

/** What the rule engine decided, and a sentence a person can read in the audit log. */
public record Decision(RequestStatus status, String reason) {
}
