package com.durdus.ticketing.user.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.durdus.ticketing.IntegrationTest;
import com.durdus.ticketing.user.User;
import com.durdus.ticketing.user.UserRepository;

@IntegrationTest
class UserTokenServiceIT {

	@Autowired
	private UserTokenService tokenService;

	@Autowired
	private UserRepository users;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock clock;

	private UUID userId;

	@BeforeEach
	void createUser() {
		cleanDatabase();
		userId = users.saveAndFlush(User.registerCustomer("Ali", "Veli", LocalDate.of(1990, 5, 20),
				"ali@example.com", "{bcrypt}hash", clock)).getId();
	}

	@AfterEach
	void cleanDatabase() {
		jdbc.update("delete from user_tokens");
		jdbc.update("delete from users");
	}

	@Test
	void aTokenCanBeUsedExactlyOnce() {
		UserTokenService.IssuedToken issued = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ofHours(72));

		assertThat(tokenService.consume(issued.rawToken(), UserTokenType.INVITATION)).contains(userId);
		assertThat(tokenService.consume(issued.rawToken(), UserTokenType.INVITATION)).isEmpty();
	}

	@Test
	void onlyAHashOfTheTokenIsStored() {
		UserTokenService.IssuedToken issued = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ofHours(72));

		String storedHash = jdbc.queryForObject("select token_hash from user_tokens", String.class);

		assertThat(storedHash).hasSize(64).isNotEqualTo(issued.rawToken());
	}

	@Test
	void anExpiredTokenIsRejected() {
		UserTokenService.IssuedToken issued = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ZERO);

		assertThat(tokenService.consume(issued.rawToken(), UserTokenType.INVITATION)).isEmpty();
	}

	@Test
	void aTokenOfAnotherTypeIsRejectedAndStaysUsable() {
		UserTokenService.IssuedToken issued = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ofHours(72));

		assertThat(tokenService.consume(issued.rawToken(), UserTokenType.PASSWORD_RESET)).isEmpty();
		assertThat(tokenService.consume(issued.rawToken(), UserTokenType.INVITATION)).contains(userId);
	}

	@Test
	void anUnknownTokenIsRejected() {
		assertThat(tokenService.consume("not-a-real-token", UserTokenType.INVITATION)).isEmpty();
	}

	@Test
	void issuingANewTokenRevokesThePreviousOneOfTheSameType() {
		UserTokenService.IssuedToken first = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ofHours(72));
		UserTokenService.IssuedToken second = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ofHours(72));

		assertThat(tokenService.consume(first.rawToken(), UserTokenType.INVITATION)).isEmpty();
		assertThat(tokenService.consume(second.rawToken(), UserTokenType.INVITATION)).contains(userId);
	}

	@Test
	void issuingATokenOfAnotherTypeDoesNotRevokeTheFirst() {
		UserTokenService.IssuedToken invitation = tokenService.issue(userId, UserTokenType.INVITATION, Duration.ofHours(72));
		tokenService.issue(userId, UserTokenType.PASSWORD_RESET, Duration.ofHours(1));

		assertThat(tokenService.consume(invitation.rawToken(), UserTokenType.INVITATION)).contains(userId);
	}

	@Test
	void concurrentUsesOfOneTokenSucceedExactlyOnce() throws Exception {
		UserTokenService.IssuedToken issued = tokenService.issue(userId, UserTokenType.ACCOUNT_RESTORE, Duration.ofHours(1));
		int attempts = 8;
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Optional<UUID>>> results = new ArrayList<>();
		for (int i = 0; i < attempts; i++) {
			results.add(executor.submit(() -> {
				start.await();
				return tokenService.consume(issued.rawToken(), UserTokenType.ACCOUNT_RESTORE);
			}));
		}
		start.countDown();

		int successes = 0;
		for (Future<Optional<UUID>> result : results) {
			if (result.get().isPresent()) {
				successes++;
			}
		}
		executor.shutdown();

		assertThat(successes).isEqualTo(1);
	}

}
