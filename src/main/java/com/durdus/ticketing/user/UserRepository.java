package com.durdus.ticketing.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

// Deleted users are filtered explicitly instead of with @SoftDelete, because deleted users can be restored.
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmailAndDeletedAtIsNull(String email);

	boolean existsByEmailAndDeletedAtIsNull(String email);

}
