package com.durdus.ticketing.user;

/**
 * ACTIVE can log in, INACTIVE was invited but has not set a password yet, SUSPENDED was blocked by an admin.
 */
public enum UserStatus {
	ACTIVE,
	INACTIVE,
	SUSPENDED
}
