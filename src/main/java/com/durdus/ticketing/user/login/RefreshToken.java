package com.durdus.ticketing.user.login;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.UuidGenerator;

/**
 * A refresh token. Only its hash is stored. Every login starts a new family; each rotation creates a new
 * token in the same family, so reuse of an old token can revoke the whole family (ADR 0006).
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken {

	@Id
	@GeneratedValue
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	private UUID userId;

	private UUID familyId;

	private String tokenHash;

	private Instant expiresAt;

	// Set when the token has been exchanged for a new one
	private Instant usedAt;

	private Instant revokedAt;

	private Instant createdAt;

	protected RefreshToken() {
	}

	RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant createdAt) {
		this.userId = userId;
		this.familyId = familyId;
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

	UUID getFamilyId() {
		return familyId;
	}

	String getTokenHash() {
		return tokenHash;
	}

	Instant getExpiresAt() {
		return expiresAt;
	}

	Instant getUsedAt() {
		return usedAt;
	}

	Instant getRevokedAt() {
		return revokedAt;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
