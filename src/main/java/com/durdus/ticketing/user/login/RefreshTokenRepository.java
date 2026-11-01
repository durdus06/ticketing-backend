package com.durdus.ticketing.user.login;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	// One atomic UPDATE: of any number of concurrent refreshes with the same token, exactly one sees a count of 1.
	@Modifying(clearAutomatically = true)
	@Query("""
			update RefreshToken t set t.usedAt = :now
			where t.tokenHash = :tokenHash and t.usedAt is null and t.revokedAt is null and t.expiresAt > :now
			""")
	int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

	@Modifying(clearAutomatically = true)
	@Query("""
			update RefreshToken t set t.revokedAt = :now
			where t.familyId = :familyId and t.revokedAt is null
			""")
	int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

	// Rows are kept for a while after expiry so that reuse of an old token can still be detected.
	@Modifying(clearAutomatically = true)
	@Query("delete from RefreshToken t where t.expiresAt < :threshold")
	int deleteExpiredBefore(@Param("threshold") Instant threshold);

}
