package com.durdus.ticketing.user.token;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserTokenRepository extends JpaRepository<UserToken, UUID> {

	Optional<UserToken> findByTokenHash(String tokenHash);

	// One atomic UPDATE: of any number of concurrent requests with the same token, exactly one sees a count of 1.
	@Modifying(clearAutomatically = true)
	@Query("""
			update UserToken t set t.consumedAt = :now
			where t.tokenHash = :tokenHash and t.tokenType = :tokenType
			and t.consumedAt is null and t.expiresAt > :now
			""")
	int consume(@Param("tokenHash") String tokenHash, @Param("tokenType") UserTokenType tokenType,
			@Param("now") Instant now);

	@Modifying(clearAutomatically = true)
	@Query("""
			update UserToken t set t.consumedAt = :now
			where t.userId = :userId and t.tokenType = :tokenType and t.consumedAt is null
			""")
	int revokeUnused(@Param("userId") UUID userId, @Param("tokenType") UserTokenType tokenType,
			@Param("now") Instant now);

}
