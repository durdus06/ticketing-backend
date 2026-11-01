package com.durdus.ticketing.user.login;

class InvalidRefreshTokenException extends RuntimeException {

	InvalidRefreshTokenException() {
		super("The session is no longer valid. Please log in again.");
	}

}
