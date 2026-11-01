package com.durdus.ticketing.user.login;

import java.util.UUID;

import com.durdus.ticketing.user.User;
import com.durdus.ticketing.user.UserRole;

record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

	record UserSummary(UUID id, String firstName, String lastName, String email, UserRole role) {

		static UserSummary from(User user) {
			return new UserSummary(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(),
					user.getRole());
		}

	}

	@Override
	public String toString() {
		return "LoginResponse[REDACTED]";
	}

}
