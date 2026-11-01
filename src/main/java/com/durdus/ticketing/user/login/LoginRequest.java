package com.durdus.ticketing.user.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Format checks only. The password policy is not applied here: it must not reveal anything about stored passwords.
record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 1000) String password) {

	// Keeps the password out of logs and error output
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=REDACTED]";
	}

}
