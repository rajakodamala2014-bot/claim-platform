package com.claims.platform.identity.repo;

import com.claims.platform.identity.domain.UserMarket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMarketRepository extends JpaRepository<UserMarket, UserMarket.Key> {
    List<UserMarket> findByUserId(String userId);
}
