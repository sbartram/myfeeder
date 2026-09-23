package org.bartram.myfeeder.controller;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.bartram.myfeeder.integration.JevNotConfiguredException;
import org.bartram.myfeeder.integration.RaindropNotConfiguredException;
import org.bartram.myfeeder.parser.FeedParseException;
import org.bartram.myfeeder.parser.OpmlParseException;
import org.bartram.myfeeder.service.FeedFetchException;
import org.bartram.myfeeder.service.NotFoundException;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springaicommunity.typesafe.exception.TypeSafeBadRequestException;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.exception.TypeSafeUnprocessableEntityException;
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

    @ExceptionHandler(FeedFetchException.class)
    public ProblemDetail handleFeedFetchException(FeedFetchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        problem.setTitle("Could not fetch feed");
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

    // Jev handlers: every detail is fixed text. TypeSafe exception messages and bodies can echo
    // request content (Phase 2 D-06), so they never reach a ProblemDetail.

    @ExceptionHandler(JevNotConfiguredException.class)
    public ProblemDetail handleJevNotConfigured(JevNotConfiguredException ex) {
        // The message is the app's own fixed text, not TypeSafe output
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Jev not configured");
        return problem;
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleJevBreakerOpen(CallNotPermittedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Jev is temporarily unavailable");
        problem.setTitle("Jev unavailable");
        return problem;
    }

    @ExceptionHandler({TypeSafeBadRequestException.class, TypeSafeUnprocessableEntityException.class})
    public ProblemDetail handleJevRejected(TypeSafeApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422),
                "Jev rejected the request (HTTP " + ex.status() + ")");
        problem.setTitle("Jev rejected the request");
        return problem;
    }

    /** Catch-all for 429, 5xx, 401/403, connection timeouts and missing answers. */
    @ExceptionHandler(TypeSafeException.class)
    public ProblemDetail handleJevFailed(TypeSafeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The Jev request failed. Try again later.");
        problem.setTitle("Jev request failed");
        return problem;
    }
}
