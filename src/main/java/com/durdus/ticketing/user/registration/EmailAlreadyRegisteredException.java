package com.durdus.ticketing.user.registration;

class EmailAlreadyRegisteredException extends RuntimeException {

	EmailAlreadyRegisteredException() {
		super("This email address is already registered");
	}

}
