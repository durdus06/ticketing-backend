package com.durdus.ticketing.user.login;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.durdus.ticketing.common.security.AuthProperties;

/**
 * Builds the refresh token cookie: HttpOnly (not readable by scripts), SameSite=Strict (never sent on
 * cross-site requests) and limited to the auth endpoints.
 */
@Component
class RefreshCookies {

	static final String NAME = "refresh_token";

	private static final String PATH = "/api/auth";

	private final AuthProperties properties;

	RefreshCookies(AuthProperties properties) {
		this.properties = properties;
	}

	ResponseCookie create(String rawToken, Duration maxAge) {
		return base(rawToken).maxAge(maxAge).build();
	}

	ResponseCookie clear() {
		return base("").maxAge(Duration.ZERO).build();
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(properties.refreshCookieSecure())
				.sameSite("Strict")
				.path(PATH);
	}

}
