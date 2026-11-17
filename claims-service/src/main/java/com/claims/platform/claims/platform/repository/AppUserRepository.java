package com.claims.platform.claims.platform.repository;

import com.claims.platform.claims.platform.entity.AppUser;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository {
    Optional<AppUser> findByUsername(String username);

    List<AppUser> list();
}
