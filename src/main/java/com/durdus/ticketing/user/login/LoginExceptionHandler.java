package com.durdus.ticketing.user.login;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LoginController.class)
class LoginExceptionHandler {

	private final RefreshCookies cookies;

	LoginExceptionHandler(RefreshCookies cookies) {
		this.cookies = cookies;
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
		return problem("INVALID_CREDENTIALS", ex);
	}

	// A rejected refresh token also clears the cookie, so the browser stops sending it.
	@ExceptionHandler(InvalidRefreshTokenException.class)
	ResponseEntity<ProblemDetail> handleInvalidRefreshToken(InvalidRefreshTokenException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
				.body(problem("INVALID_REFRESH_TOKEN", ex));
	}

	private static ProblemDetail problem(String code, RuntimeException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
		problem.setProperty("code", code);
		return problem;
	}

}
