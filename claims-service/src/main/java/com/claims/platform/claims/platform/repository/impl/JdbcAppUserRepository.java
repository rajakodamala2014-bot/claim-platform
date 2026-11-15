package com.claims.platform.claims.platform.repository.impl;

import com.claims.platform.claims.platform.common.JdbcSupport;
import com.claims.platform.claims.platform.entity.AppUser;
import com.claims.platform.claims.platform.repository.AppUserRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class JdbcAppUserRepository implements AppUserRepository {
    private final JdbcClient jdbc;

    public JdbcAppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppUser> findByUsername(String username) {
        return jdbc.sql("""
                        SELECT user_id, username, password_hash, display_name, role, markets, team_id, locale, enabled
                        FROM platform.app_user WHERE username = :username
                        """)
                .param("username", username)
                .query((rs, n) -> new AppUser(rs.getString("user_id"), rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("display_name"), rs.getString("role"), JdbcSupport.strings(rs, "markets"),
                        rs.getString("team_id"), rs.getString("locale"), rs.getBoolean("enabled")))
                .optional();
    }
}
