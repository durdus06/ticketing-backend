package com.durdus.ticketing.user.token;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * A single-use token sent to a user by email. Only the hash of the token is stored, so a leaked database
 * does not expose usable links.
 */
@Entity
@Table(name = "user_tokens")
class UserToken {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID userId;

	@Enumerated(EnumType.STRING)
	private UserTokenType tokenType;

	private String tokenHash;

	private Instant expiresAt;

	// Set when the token is used, and also when it is revoked by a newer token
	private Instant consumedAt;

	private Instant createdAt;

	protected UserToken() {
	}

	UserToken(UUID userId, UserTokenType tokenType, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.userId = userId;
		this.tokenType = tokenType;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	UUID getId() {
		return id;
	}

	UUID getUserId() {
		return userId;
	}

	UserTokenType getTokenType() {
		return tokenType;
	}

	String getTokenHash() {
		return tokenHash;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

	Instant getConsumedAt() {
		return consumedAt;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
