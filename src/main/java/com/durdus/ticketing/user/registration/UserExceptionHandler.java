package com.durdus.ticketing.user.registration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.durdus.ticketing.user.UnderageUserException;

@RestControllerAdvice
class UserExceptionHandler {

	// The "code" property is a stable, machine-readable contract for clients. The detail text may change.
	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
		return problem(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", ex);
	}

	@ExceptionHandler(UnderageUserException.class)
	ProblemDetail handleUnderage(UnderageUserException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "UNDERAGE_USER", ex);
	}

	@ExceptionHandler(WeakPasswordException.class)
	ProblemDetail handleWeakPassword(WeakPasswordException ex) {
		return problem(HttpStatus.UNPROCESSABLE_CONTENT, "WEAK_PASSWORD", ex);
	}

	private static ProblemDetail problem(HttpStatus status, String code, RuntimeException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
		problem.setProperty("code", code);
		return problem;
	}

}
