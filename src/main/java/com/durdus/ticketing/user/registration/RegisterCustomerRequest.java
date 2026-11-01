package com.durdus.ticketing.user.registration;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

record RegisterCustomerRequest(
		@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotNull @Past LocalDate dateOfBirth,
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(max = 128) String password) {

	// Never let the password or personal data end up in logs
	@Override
	public String toString() {
		return "RegisterCustomerRequest[REDACTED]";
	}

}
