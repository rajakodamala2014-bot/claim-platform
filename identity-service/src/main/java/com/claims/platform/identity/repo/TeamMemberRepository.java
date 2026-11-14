package com.claims.platform.identity.repo;

import com.claims.platform.identity.domain.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, TeamMember.Key> {
    Optional<TeamMember> findFirstByUserId(String userId);
}
