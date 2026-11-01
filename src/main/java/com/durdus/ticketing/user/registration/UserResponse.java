package com.durdus.ticketing.user.registration;

import java.util.UUID;

import com.durdus.ticketing.user.User;
import com.durdus.ticketing.user.UserRole;
import com.durdus.ticketing.user.UserStatus;

record UserResponse(UUID id, String firstName, String lastName, String email, UserRole role, UserStatus status) {

	static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
				user.getRole(), user.getStatus());
	}

}
