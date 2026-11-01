package com.durdus.ticketing.user.login;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.durdus.ticketing.common.security.AuthProperties;
import com.durdus.ticketing.user.User;

@Service
class AccessTokenService {

	private final JwtEncoder encoder;

	private final AuthProperties properties;

	private final Clock clock;

	AccessTokenService(JwtEncoder encoder, AuthProperties properties, Clock clock) {
		this.encoder = encoder;
		this.properties = properties;
		this.clock = clock;
	}

	record IssuedAccessToken(String token, long expiresInSeconds) {

		@Override
		public String toString() {
			return "IssuedAccessToken[REDACTED]";
		}

	}

	// Carries only the user id and role. Personal data stays out of the token, as it is readable by the client.
	IssuedAccessToken issue(User user) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(now.plus(properties.accessTokenTtl()))
				.claim("role", user.getRole().name())
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new IssuedAccessToken(token, properties.accessTokenTtl().toSeconds());
	}

}
