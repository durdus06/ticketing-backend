package com.durdus.ticketing.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class User {

	static final int MINIMUM_AGE = 18;

	private static final String ANONYMIZED_NAME = "deleted";

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private String firstName;

	private String lastName;

	private LocalDate dateOfBirth;

	private String email;

	private String passwordHash;

	@Enumerated(EnumType.STRING)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	private UserStatus status;

	private Instant createdAt;

	private Instant updatedAt;

	private Instant deletedAt;

	private Instant anonymizeAfter;

	private Instant anonymizedAt;

	protected User() {
	}

	public static User registerCustomer(String firstName, String lastName, LocalDate dateOfBirth, String email,
			String passwordHash, Clock clock) {
		checkMinimumAge(dateOfBirth, clock);
		Instant now = clock.instant();
		User user = new User();
		user.firstName = firstName.strip();
		user.lastName = lastName.strip();
		user.dateOfBirth = dateOfBirth;
		user.email = normalizeEmail(email);
		user.passwordHash = passwordHash;
		user.role = UserRole.CUSTOMER;
		user.status = UserStatus.ACTIVE;
		user.createdAt = now;
		user.updatedAt = now;
		return user;
	}

	// Locale.ROOT matters: with a Turkish default locale "I" would otherwise become a dotless "ı".
	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	// "Today" is taken in the clock's zone (UTC): the date of birth is self-declared, so hour-level precision adds no assurance.
	public static void checkMinimumAge(LocalDate dateOfBirth, Clock clock) {
		LocalDate today = LocalDate.now(clock);
		if (dateOfBirth.plusYears(MINIMUM_AGE).isAfter(today)) {
			throw new UnderageUserException(MINIMUM_AGE);
		}
	}

	boolean canLogin() {
		return status == UserStatus.ACTIVE && deletedAt == null;
	}

	boolean isDeleted() {
		return deletedAt != null;
	}

	boolean isAnonymized() {
		return anonymizedAt != null;
	}

	void suspend(Instant now) {
		if (status != UserStatus.ACTIVE) {
			throw new IllegalStateException("Only an active user can be suspended");
		}
		status = UserStatus.SUSPENDED;
		updatedAt = now;
	}

	void reactivate(Instant now) {
		if (status != UserStatus.SUSPENDED) {
			throw new IllegalStateException("Only a suspended user can be reactivated");
		}
		status = UserStatus.ACTIVE;
		updatedAt = now;
	}

	// Soft delete. The status is left untouched so that deleting and restoring never lifts a suspension.
	void delete(Instant now, Duration anonymizationDelay) {
		if (isDeleted()) {
			throw new IllegalStateException("User is already deleted");
		}
		deletedAt = now;
		anonymizeAfter = now.plus(anonymizationDelay);
		updatedAt = now;
	}

	void restore(Instant now) {
		if (!isDeleted()) {
			throw new IllegalStateException("User is not deleted");
		}
		if (isAnonymized()) {
			throw new IllegalStateException("An anonymized user cannot be restored");
		}
		deletedAt = null;
		anonymizeAfter = null;
		updatedAt = now;
	}

	// Irreversible. The password hash is removed too, and as an ACTIVE user must have a password the status
	// becomes INACTIVE.
	void anonymize(Instant now) {
		if (!isDeleted()) {
			throw new IllegalStateException("Only a deleted user can be anonymized");
		}
		if (isAnonymized()) {
			throw new IllegalStateException("User is already anonymized");
		}
		if (now.isBefore(anonymizeAfter)) {
			throw new IllegalStateException("The anonymization date has not been reached");
		}
		firstName = ANONYMIZED_NAME;
		lastName = ANONYMIZED_NAME;
		dateOfBirth = null;
		email = "anonymized-" + id + "@anonymized.invalid";
		passwordHash = null;
		status = UserStatus.INACTIVE;
		anonymizedAt = now;
		updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public String getFirstName() {
		return firstName;
	}

	public String getLastName() {
		return lastName;
	}

	LocalDate getDateOfBirth() {
		return dateOfBirth;
	}

	public String getEmail() {
		return email;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	public UserRole getRole() {
		return role;
	}

	public UserStatus getStatus() {
		return status;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	Instant getUpdatedAt() {
		return updatedAt;
	}

	Instant getDeletedAt() {
		return deletedAt;
	}

	Instant getAnonymizeAfter() {
		return anonymizeAfter;
	}

	Instant getAnonymizedAt() {
		return anonymizedAt;
	}

}
