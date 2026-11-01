package com.durdus.ticketing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class UserTest {

	private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private static final LocalDate ADULT = LocalDate.of(1990, 5, 20);

	private static User customer() {
		return User.registerCustomer(" Ali ", " Veli ", ADULT, "ali@example.com", "hash", CLOCK);
	}

	@Test
	void registersAnActiveCustomer() {
		User user = customer();

		assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
		assertThat(user.getFirstName()).isEqualTo("Ali");
		assertThat(user.getLastName()).isEqualTo("Veli");
		assertThat(user.getCreatedAt()).isEqualTo(NOW);
		assertThat(user.canLogin()).isTrue();
	}

	@Test
	void normalizesEmailWithTurkishDefaultLocale() {
		Locale original = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr-TR"));
		try {
			User user = User.registerCustomer("Ali", "Veli", ADULT, "  INFO@Example.COM ", "hash", CLOCK);

			assertThat(user.getEmail()).isEqualTo("info@example.com");
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@Test
	void acceptsACustomerWhoTurns18Today() {
		LocalDate dateOfBirth = LocalDate.of(2008, 10, 17);

		assertThatCode(() -> User.checkMinimumAge(dateOfBirth, CLOCK)).doesNotThrowAnyException();
	}

	@Test
	void rejectsACustomerWhoTurns18Tomorrow() {
		LocalDate dateOfBirth = LocalDate.of(2008, 10, 18);

		assertThatThrownBy(() -> User.checkMinimumAge(dateOfBirth, CLOCK)).isInstanceOf(UnderageUserException.class);
	}

	@Test
	void calculatesTheAgeInUtc() {
		// 21:30 UTC on 17 October is already 18 October in zones ahead of UTC, but the check uses the UTC date
		Clock lateEvening = Clock.fixed(Instant.parse("2026-10-17T21:30:00Z"), ZoneOffset.UTC);
		LocalDate dateOfBirth = LocalDate.of(2008, 10, 18);

		assertThatThrownBy(() -> User.checkMinimumAge(dateOfBirth, lateEvening))
				.isInstanceOf(UnderageUserException.class);
	}

	@Test
	void deleteSchedulesAnonymizationAndKeepsTheStatus() {
		User user = customer();
		user.suspend(NOW);

		user.delete(NOW, Duration.ofDays(30));

		assertThat(user.isDeleted()).isTrue();
		assertThat(user.getAnonymizeAfter()).isEqualTo(NOW.plus(Duration.ofDays(30)));
		assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
		assertThat(user.canLogin()).isFalse();
	}

	@Test
	void cannotDeleteTwice() {
		User user = customer();
		user.delete(NOW, Duration.ofDays(30));

		assertThatThrownBy(() -> user.delete(NOW, Duration.ofDays(30))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void restoreDoesNotLiftASuspension() {
		User user = customer();
		user.suspend(NOW);
		user.delete(NOW, Duration.ofDays(30));

		user.restore(NOW.plusSeconds(60));

		assertThat(user.isDeleted()).isFalse();
		assertThat(user.getAnonymizeAfter()).isNull();
		assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
		assertThat(user.canLogin()).isFalse();
	}

	@Test
	void cannotRestoreAUserWhoIsNotDeleted() {
		User user = customer();

		assertThatThrownBy(() -> user.restore(NOW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotAnonymizeBeforeTheScheduledDate() {
		User user = customer();
		user.delete(NOW, Duration.ofDays(30));

		assertThatThrownBy(() -> user.anonymize(NOW.plus(Duration.ofDays(29)))).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cannotAnonymizeAUserWhoIsNotDeleted() {
		User user = customer();

		assertThatThrownBy(() -> user.anonymize(NOW)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void anonymizeRemovesPersonalDataAndCannotBeUndone() {
		User user = customer();
		user.delete(NOW, Duration.ofDays(30));
		Instant due = NOW.plus(Duration.ofDays(30));

		user.anonymize(due);

		assertThat(user.isAnonymized()).isTrue();
		assertThat(user.getFirstName()).isEqualTo("deleted");
		assertThat(user.getLastName()).isEqualTo("deleted");
		assertThat(user.getDateOfBirth()).isNull();
		assertThat(user.getEmail()).endsWith("@anonymized.invalid").doesNotContain("ali@example.com");
		assertThat(user.getPasswordHash()).isNull();
		assertThat(user.getStatus()).isEqualTo(UserStatus.INACTIVE);
		assertThatThrownBy(() -> user.restore(due)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void onlyActiveUsersCanBeSuspendedAndOnlySuspendedUsersReactivated() {
		User user = customer();

		assertThatThrownBy(() -> user.reactivate(NOW)).isInstanceOf(IllegalStateException.class);
		user.suspend(NOW);
		assertThatThrownBy(() -> user.suspend(NOW)).isInstanceOf(IllegalStateException.class);
		user.reactivate(NOW);
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}

}
