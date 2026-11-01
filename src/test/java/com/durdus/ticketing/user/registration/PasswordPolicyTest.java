package com.durdus.ticketing.user.registration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	@Test
	void acceptsAPasswordOfMinimumLength() {
		assertThatCode(() -> PasswordPolicy.validate("a".repeat(10))).doesNotThrowAnyException();
	}

	@Test
	void rejectsAPasswordThatIsTooShort() {
		assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(9))).isInstanceOf(WeakPasswordException.class);
	}

	@Test
	void acceptsAPasswordOfMaximumBytes() {
		assertThatCode(() -> PasswordPolicy.validate("a".repeat(72))).doesNotThrowAnyException();
	}

	@Test
	void rejectsAPasswordLongerThanBcryptCanHandle() {
		assertThatThrownBy(() -> PasswordPolicy.validate("a".repeat(73))).isInstanceOf(WeakPasswordException.class);
	}

	@Test
	void countsBytesNotCharacters() {
		// 40 characters but 80 bytes in UTF-8
		assertThatThrownBy(() -> PasswordPolicy.validate("ğ".repeat(40))).isInstanceOf(WeakPasswordException.class);
	}

}
