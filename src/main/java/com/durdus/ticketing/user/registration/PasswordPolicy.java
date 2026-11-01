package com.durdus.ticketing.user.registration;

import java.nio.charset.StandardCharsets;

final class PasswordPolicy {

	static final int MIN_LENGTH = 10;

	// bcrypt ignores or rejects everything after 72 bytes
	static final int MAX_BYTES = 72;

	private PasswordPolicy() {
	}

	static void validate(String password) {
		if (password.codePointCount(0, password.length()) < MIN_LENGTH) {
			throw new WeakPasswordException("Password must be at least " + MIN_LENGTH + " characters long");
		}
		if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
			throw new WeakPasswordException("Password must be at most " + MAX_BYTES + " bytes long");
		}
	}

}
