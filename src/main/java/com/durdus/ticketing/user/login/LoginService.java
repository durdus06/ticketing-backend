package com.durdus.ticketing.user.login;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.durdus.ticketing.user.User;
import com.durdus.ticketing.user.UserRepository;

@Service
class LoginService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AccessTokenService accessTokens;

	private final RefreshTokenService refreshTokens;

	// Verified against when the email is unknown, so that the response time does not reveal which emails exist.
	private final String dummyHash;

	LoginService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
			RefreshTokenService refreshTokens) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
	}

	record Session(User user, AccessTokenService.IssuedAccessToken accessToken,
			RefreshTokenService.IssuedRefreshToken refreshToken) {
	}

	@Transactional
	Session login(String email, String rawPassword) {
		Optional<User> found = users.findByEmailAndDeletedAtIsNull(User.normalizeEmail(email));
		if (found.isEmpty()) {
			passwordEncoder.matches(rawPassword, dummyHash);
			throw new InvalidCredentialsException();
		}
		User user = found.get();
		boolean passwordMatches = user.passwordMatches(rawPassword, passwordEncoder);
		if (!passwordMatches || !user.canLogin()) {
			throw new InvalidCredentialsException();
		}
		return new Session(user, accessTokens.issue(user), refreshTokens.startFamily(user.getId()));
	}

	// No @Transactional here: the revocation done by a rejected (replayed) token must be committed, which
	// RefreshTokenService.rotate does in its own transaction and then returns normally.
	Session refresh(String rawRefreshToken) {
		RefreshTokenService.IssuedRefreshToken rotated = refreshTokens.rotate(rawRefreshToken)
				.orElseThrow(InvalidRefreshTokenException::new);
		// A suspended or deleted user gets no new access token, and the family is closed.
		User user = users.findById(rotated.userId()).filter(User::canLogin).orElse(null);
		if (user == null) {
			refreshTokens.revokeFamilyOf(rotated.rawToken());
			throw new InvalidRefreshTokenException();
		}
		return new Session(user, accessTokens.issue(user), rotated);
	}

	void logout(String rawRefreshToken) {
		refreshTokens.revokeFamilyOf(rawRefreshToken);
	}

}
