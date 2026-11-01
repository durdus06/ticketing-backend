package com.durdus.ticketing.user.registration;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.durdus.ticketing.user.User;

@RestController
@RequestMapping("/api/auth")
class UserRegistrationController {

	private final UserRegistrationService registrationService;

	UserRegistrationController(UserRegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse register(@Valid @RequestBody RegisterCustomerRequest request) {
		User user = registrationService.registerCustomer(request.firstName(), request.lastName(),
				request.dateOfBirth(), request.email(), request.password());
		return UserResponse.from(user);
	}

}
