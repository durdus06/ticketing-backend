package com.durdus.ticketing.user;

public class UnderageUserException extends RuntimeException {

	UnderageUserException(int minimumAge) {
		super("You must be at least " + minimumAge + " years old to register");
	}

}
