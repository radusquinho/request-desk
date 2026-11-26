package com.radusquinho.requestdesk.service;

/** A logged-in user tried something their role or relation to the request does not allow. */
public class ForbiddenActionException extends RuntimeException {
    public ForbiddenActionException(String message) {
        super(message);
    }
}
