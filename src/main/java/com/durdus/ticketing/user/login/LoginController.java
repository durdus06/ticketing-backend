package com.durdus.ticketing.user.login;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class LoginController {

	private final LoginService loginService;

	private final RefreshCookies cookies;

	LoginController(LoginService loginService, RefreshCookies cookies) {
		this.loginService = loginService;
		this.cookies = cookies;
	}

	@PostMapping("/login")
	ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return respond(loginService.login(request.email(), request.password()));
	}

	@PostMapping("/refresh")
	ResponseEntity<LoginResponse> refresh(
			@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		return respond(loginService.refresh(refreshToken));
	}

	@PostMapping("/logout")
	ResponseEntity<Void> logout(@CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {
		if (refreshToken != null && !refreshToken.isBlank()) {
			loginService.logout(refreshToken);
		}
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
	}

	private ResponseEntity<LoginResponse> respond(LoginService.Session session) {
		LoginResponse body = new LoginResponse(session.accessToken().token(), "Bearer",
				session.accessToken().expiresInSeconds(), LoginResponse.UserSummary.from(session.user()));
		RefreshTokenService.IssuedRefreshToken refresh = session.refreshToken();
		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookies.create(refresh.rawToken(), refresh.timeToLive()).toString())
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.body(body);
	}

}
