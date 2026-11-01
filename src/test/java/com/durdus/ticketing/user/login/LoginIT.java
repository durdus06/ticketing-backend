package com.durdus.ticketing.user.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.durdus.ticketing.IntegrationTest;

@IntegrationTest
class LoginIT {

	private static final String EMAIL = "ali@example.com";

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanDatabase() {
		jdbc.update("delete from refresh_tokens");
		jdbc.update("delete from user_tokens");
		jdbc.update("delete from users");
	}

	// Cleaning and registering are one method: the order of several @BeforeEach methods is not defined.
	@BeforeEach
	void registerUser() throws Exception {
		cleanDatabase();
		String body = """
				{"firstName": "Ali", "lastName": "Veli", "dateOfBirth": "1990-05-20", "email": "%s", "password": "%s"}
				""".formatted(EMAIL, PASSWORD);
		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	private ResultActions login(String email, String password) throws Exception {
		String body = """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password);
		return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions refresh(Cookie cookie) throws Exception {
		return cookie == null ? mockMvc.perform(post("/api/auth/refresh"))
				: mockMvc.perform(post("/api/auth/refresh").cookie(cookie));
	}

	private Cookie refreshCookieOf(ResultActions result) {
		Cookie cookie = result.andReturn().getResponse().getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	@Test
	void logsInAndReturnsAnAccessTokenAndAnHttpOnlyRefreshCookie() throws Exception {
		ResultActions result = login("Ali@Example.com", PASSWORD);

		result.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(600))
				.andExpect(jsonPath("$.user.email").value(EMAIL))
				.andExpect(jsonPath("$.user.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.user.passwordHash").doesNotExist());
		MockHttpServletResponse response = result.andReturn().getResponse();
		assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
		String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
	}

	@Test
	void storesOnlyAHashOfTheRefreshToken() throws Exception {
		Cookie cookie = refreshCookieOf(login(EMAIL, PASSWORD));

		String storedHash = jdbc.queryForObject("select token_hash from refresh_tokens", String.class);
		assertThat(storedHash).isNotEqualTo(cookie.getValue()).hasSize(64);
	}

	@Test
	void rejectsWrongPasswordAndUnknownEmailWithTheSameResponse() throws Exception {
		String wrongPassword = login(EMAIL, "wrong-password-123").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();
		String unknownEmail = login("nobody@example.com", "wrong-password-123").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andReturn().getResponse().getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void rejectsSuspendedAndDeletedUsersWithTheSameGenericError() throws Exception {
		jdbc.update("update users set status = 'SUSPENDED'");
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		jdbc.update("update users set status = 'ACTIVE', deleted_at = now(), anonymize_after = now() + interval '30 days'");
		login(EMAIL, PASSWORD).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void rejectsABlankPasswordAsAValidationError() throws Exception {
		login(EMAIL, "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void accessTokenOpensProtectedEndpointsAndMissingOrTamperedTokensDoNot() throws Exception {
		String accessToken = jsonField(login(EMAIL, PASSWORD), "accessToken");

		// The route does not exist, so an authenticated request reaches the dispatcher and gets 404, not 401
		mockMvc.perform(get("/api/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/unknown").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken + "x"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshRotatesTheTokenAndReturnsANewAccessToken() throws Exception {
		Cookie first = refreshCookieOf(login(EMAIL, PASSWORD));

		ResultActions refreshed = refresh(first).andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.user.email").value(EMAIL));

		assertThat(refreshCookieOf(refreshed).getValue()).isNotEqualTo(first.getValue());
	}

	@Test
	void replayingAnAlreadyUsedRefreshTokenRevokesTheWholeFamily() throws Exception {
		Cookie first = refreshCookieOf(login(EMAIL, PASSWORD));
		Cookie second = refreshCookieOf(refresh(first).andExpect(status().isOk()));

		refresh(first).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

		// The legitimate, newest token is dead too
		refresh(second).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshWithoutOrWithAnUnknownCookieIsRejectedAndClearsTheCookie() throws Exception {
		refresh(null).andExpect(status().isUnauthorized());
		ResultActions unknown = refresh(new Cookie("refresh_token", "not-a-real-token"))
				.andExpect(status().isUnauthorized());

		assertThat(unknown.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
	}

	@Test
	void refreshIsRejectedAfterTheTokenExpired() throws Exception {
		Cookie cookie = refreshCookieOf(login(EMAIL, PASSWORD));
		jdbc.update("update refresh_tokens set expires_at = now() - interval '1 minute'");

		refresh(cookie).andExpect(status().isUnauthorized());
	}

	@Test
	void refreshIsRejectedOnceTheUserIsSuspended() throws Exception {
		Cookie cookie = refreshCookieOf(login(EMAIL, PASSWORD));
		jdbc.update("update users set status = 'SUSPENDED'");

		refresh(cookie).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
	}

	@Test
	void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
		Cookie cookie = refreshCookieOf(login(EMAIL, PASSWORD));

		ResultActions logout = mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent());

		assertThat(logout.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
		refresh(cookie).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutWithoutACookieStillSucceeds() throws Exception {
		mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
	}

	private String jsonField(ResultActions result, String field) throws Exception {
		String json = result.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(json, "$." + field);
	}

}
