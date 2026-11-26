package com.radusquinho.requestdesk.web;

import com.radusquinho.requestdesk.service.ForbiddenActionException;
import com.radusquinho.requestdesk.service.NotFoundException;
import com.radusquinho.requestdesk.web.Dtos.ErrorView;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns domain errors into small JSON messages with the right HTTP status. */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorView notFound(NotFoundException e) {
        return new ErrorView(e.getMessage());
    }

    @ExceptionHandler(ForbiddenActionException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorView forbidden(ForbiddenActionException e) {
        return new ErrorView(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorView conflict(IllegalStateException e) {
        return new ErrorView(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorView badRequest(IllegalArgumentException e) {
        return new ErrorView(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorView invalid(MethodArgumentNotValidException e) {
        String fields = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return new ErrorView("Invalid request: " + fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorView unreadable(HttpMessageNotReadableException e) {
        return new ErrorView("Malformed JSON or unknown value");
    }
}
