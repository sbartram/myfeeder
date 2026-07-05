package org.bartram.myfeeder.controller;

import org.bartram.myfeeder.integration.RaindropNotConfiguredException;
import org.bartram.myfeeder.parser.FeedParseException;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.bartram.myfeeder.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(FeedParseException.class)
    public ProblemDetail handleFeedParseException(FeedParseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        problem.setTitle("Could not parse feed");
        return problem;
    }

    @ExceptionHandler(OpmlParseException.class)
    public ProblemDetail handleOpmlParseException(OpmlParseException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Could not parse OPML");
        return problem;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Configuration error");
        return problem;
    }

    @ExceptionHandler(RaindropNotConfiguredException.class)
    public ProblemDetail handleRaindropNotConfigured(RaindropNotConfiguredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Raindrop not configured");
        return problem;
    }
}
