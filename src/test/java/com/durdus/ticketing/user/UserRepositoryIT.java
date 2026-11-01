package com.durdus.ticketing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.durdus.ticketing.IntegrationTest;

@IntegrationTest
@Transactional
class UserRepositoryIT {

	@Autowired
	private UserRepository users;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Clock clock;

	// Other integration tests commit their data, so start from an empty table. Rolled back with the test.
	@BeforeEach
	void emptyUsersTable() {
		jdbc.update("delete from user_tokens");
		jdbc.update("delete from users");
	}

	private User customer(String email) {
		return User.registerCustomer("Ali", "Veli", LocalDate.of(1990, 5, 20), email, "{bcrypt}hash", clock);
	}

	@Test
	void savesAndReadsAUserWithAVersion7Id() {
		User saved = users.saveAndFlush(customer("ali@example.com"));

		User found = users.findById(saved.getId()).orElseThrow();

		assertThat(found.getId().version()).isEqualTo(7);
		assertThat(found.getEmail()).isEqualTo("ali@example.com");
		assertThat(found.getRole()).isEqualTo(UserRole.CUSTOMER);
		assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(found.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 20));
	}

	@Test
	void findsAnActiveUserByEmail() {
		users.saveAndFlush(customer("ali@example.com"));

		assertThat(users.findByEmailAndDeletedAtIsNull("ali@example.com")).isPresent();
		assertThat(users.existsByEmailAndDeletedAtIsNull("ali@example.com")).isTrue();
	}

	@Test
	void doesNotFindADeletedUserByEmail() {
		User user = customer("ali@example.com");
		user.delete(clock.instant(), Duration.ofDays(30));
		users.saveAndFlush(user);

		assertThat(users.findByEmailAndDeletedAtIsNull("ali@example.com")).isEmpty();
		assertThat(users.existsByEmailAndDeletedAtIsNull("ali@example.com")).isFalse();
	}

	@Test
	void rejectsTwoActiveUsersWithTheSameEmail() {
		users.saveAndFlush(customer("ali@example.com"));

		assertThatThrownBy(() -> users.saveAndFlush(customer("ali@example.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void allowsANewUserWithTheEmailOfADeletedUser() {
		User deleted = customer("ali@example.com");
		deleted.delete(clock.instant(), Duration.ofDays(30));
		users.saveAndFlush(deleted);

		users.saveAndFlush(customer("ali@example.com"));

		assertThat(users.findByEmailAndDeletedAtIsNull("ali@example.com")).isPresent();
	}

	@Test
	void persistsAnAnonymizedUser() {
		User user = customer("ali@example.com");
		user.delete(clock.instant(), Duration.ZERO);
		user.anonymize(clock.instant());

		User saved = users.saveAndFlush(user);

		User found = users.findById(saved.getId()).orElseThrow();
		assertThat(found.isAnonymized()).isTrue();
		assertThat(found.getDateOfBirth()).isNull();
		assertThat(found.getPasswordHash()).isNull();
	}

	@Test
	void databaseRejectsAnUppercaseEmail() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into users (id, first_name, last_name, date_of_birth, email, password_hash, role, status,
				created_at, updated_at)
				values (gen_random_uuid(), 'A', 'B', date '1990-01-01', 'ALI@EXAMPLE.COM', 'hash', 'CUSTOMER', 'ACTIVE',
				now(), now())
				""")).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_users_email_lowercase");
	}

}
