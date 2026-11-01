package com.durdus.ticketing.user.registration;

import java.time.Clock;
import java.time.LocalDate;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.durdus.ticketing.user.User;
import com.durdus.ticketing.user.UserRepository;

@Service
class UserRegistrationService {

	private static final String ACTIVE_EMAIL_INDEX = "uq_users_email_active";

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	UserRegistrationService(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	@Transactional
	User registerCustomer(String firstName, String lastName, LocalDate dateOfBirth, String email, String rawPassword) {
		// Cheap checks first, so rejected requests never pay for password hashing
		User.checkMinimumAge(dateOfBirth, clock);
		PasswordPolicy.validate(rawPassword);
		String normalizedEmail = User.normalizeEmail(email);
		if (users.existsByEmailAndDeletedAtIsNull(normalizedEmail)) {
			throw new EmailAlreadyRegisteredException();
		}
		User user = User.registerCustomer(firstName, lastName, dateOfBirth, normalizedEmail,
				passwordEncoder.encode(rawPassword), clock);
		try {
			return users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Two concurrent registrations can both pass the check above. The unique index is the real guard.
			if (violatesActiveEmailIndex(ex)) {
				throw new EmailAlreadyRegisteredException();
			}
			throw ex;
		}
	}

	private static boolean violatesActiveEmailIndex(Throwable throwable) {
		for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation
					&& ACTIVE_EMAIL_INDEX.equalsIgnoreCase(violation.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

}
