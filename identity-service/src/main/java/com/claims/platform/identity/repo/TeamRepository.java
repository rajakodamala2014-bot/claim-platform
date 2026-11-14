package com.claims.platform.identity.repo;

import com.claims.platform.identity.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, String> {
}
