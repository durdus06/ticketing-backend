package com.durdus.ticketing.user.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.durdus.ticketing.IntegrationTest;

@IntegrationTest
class UserRegistrationIT {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void cleanDatabase() {
		jdbc.update("delete from user_tokens");
		jdbc.update("delete from users");
	}

	private int register(String email, String dateOfBirth, String password) throws Exception {
		String body = """
				{"firstName": "Ali", "lastName": "Veli", "dateOfBirth": "%s", "email": "%s", "password": "%s"}
				""".formatted(dateOfBirth, email, password);
		return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andReturn().getResponse().getStatus();
	}

	@Test
	void registersACustomerAndStoresOnlyAHashOfThePassword() throws Exception {
		String body = """
				{"firstName": "Ali", "lastName": "Veli", "dateOfBirth": "1990-05-20", "email": "Ali@Example.com",
				"password": "%s"}
				""".formatted(PASSWORD);

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.email").value("ali@example.com"))
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());

		String storedHash = jdbc.queryForObject("select password_hash from users where email = 'ali@example.com'",
				String.class);
		assertThat(storedHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
	}

	@Test
	void ignoresRoleStatusAndIdSentByTheClient() throws Exception {
		String body = """
				{"firstName": "Ali", "lastName": "Veli", "dateOfBirth": "1990-05-20", "email": "ali@example.com",
				"password": "%s", "role": "ADMIN", "status": "SUSPENDED", "id": "00000000-0000-0000-0000-000000000000"}
				""".formatted(PASSWORD);

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.role").value("CUSTOMER"))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not("00000000-0000-0000-0000-000000000000")));

		assertThat(jdbc.queryForObject("select role from users where email = 'ali@example.com'", String.class))
				.isEqualTo("CUSTOMER");
	}

	@Test
	void rejectsAnInvalidRequestWithoutEchoingTheValues() throws Exception {
		String body = """
				{"firstName": "", "lastName": "Veli", "dateOfBirth": "1990-05-20", "email": "not-an-email",
				"password": "%s"}
				""".formatted(PASSWORD);

		mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.firstName").exists())
				.andExpect(jsonPath("$.errors.email").exists())
				.andExpect(jsonPath("$.errors.lastName").doesNotExist());
	}

	@Test
	void rejectsACustomerUnder18() throws Exception {
		String turns18Tomorrow = LocalDate.now(ZoneOffset.UTC).minusYears(18).plusDays(1).toString();

		assertThat(register("kid@example.com", turns18Tomorrow, PASSWORD)).isEqualTo(422);
		assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
	}

	@Test
	void rejectsAWeakPassword() throws Exception {
		assertThat(register("ali@example.com", "1990-05-20", "short")).isEqualTo(422);
	}

	@Test
	void rejectsADuplicateEmailIgnoringCase() throws Exception {
		assertThat(register("ali@example.com", "1990-05-20", PASSWORD)).isEqualTo(201);

		assertThat(register("ALI@example.com", "1990-05-20", PASSWORD)).isEqualTo(409);
	}

	@Test
	void concurrentRegistrationsWithTheSameEmailCreateExactlyOneUser() throws Exception {
		int attempts = 8;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Integer>> results = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return register("race@example.com", "1990-05-20", PASSWORD);
			}));
		}
		start.countDown();

		List<Integer> statuses = new ArrayList<>();
		for (Future<Integer> result : results) {
			statuses.add(result.get());
		}
		executor.shutdown();

		assertThat(statuses).filteredOn(code -> code == 201).hasSize(1);
		assertThat(statuses).filteredOn(code -> code == 409).hasSize(attempts - 1);
		assertThat(jdbc.queryForObject("select count(*) from users where email = 'race@example.com'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void requestToStringHidesAllValues() {
		RegisterCustomerRequest request = new RegisterCustomerRequest("Ali", "Veli", LocalDate.of(1990, 5, 20),
				"ali@example.com", PASSWORD);

		assertThat(request.toString()).doesNotContain(PASSWORD).doesNotContain("ali@example.com");
	}

}
