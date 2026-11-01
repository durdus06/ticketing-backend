package com.durdus.ticketing.user.login;

// One message for every reason (unknown email, wrong password, suspended, deleted): the client must not learn which.
class InvalidCredentialsException extends RuntimeException {

	InvalidCredentialsException() {
		super("Invalid email or password");
	}

}
