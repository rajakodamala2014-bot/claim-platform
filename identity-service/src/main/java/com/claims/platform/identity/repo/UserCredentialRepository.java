package com.claims.platform.identity.repo;

import com.claims.platform.identity.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCredentialRepository extends JpaRepository<UserCredential, String> {
}
