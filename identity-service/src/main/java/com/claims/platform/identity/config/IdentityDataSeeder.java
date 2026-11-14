package com.claims.platform.identity.config;

import com.claims.platform.identity.domain.AppUser;
import com.claims.platform.identity.domain.PolicySnapshot;
import com.claims.platform.identity.domain.Team;
import com.claims.platform.identity.domain.TeamMember;
import com.claims.platform.identity.domain.UserCredential;
import com.claims.platform.identity.domain.UserMarket;
import com.claims.platform.identity.repo.AppUserRepository;
import com.claims.platform.identity.repo.PolicyRepository;
import com.claims.platform.identity.repo.TeamMemberRepository;
import com.claims.platform.identity.repo.TeamRepository;
import com.claims.platform.identity.repo.UserCredentialRepository;
import com.claims.platform.identity.repo.UserMarketRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Configuration
public class IdentityDataSeeder {
    private static final String PASSWORD = "Password123!";

    @Bean
    CommandLineRunner seedIdentity(AppUserRepository users, UserCredentialRepository credentials, TeamRepository teams,
                                   TeamMemberRepository members, UserMarketRepository markets, PolicyRepository policies,
                                   PasswordEncoder encoder) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            Instant now = Instant.now();
            user(users, credentials, encoder, "u-1001", "priya@example.com", "Priya Nair", "CLAIMANT", now);
            user(users, credentials, encoder, "u-2001", "arun@example.com", "Arun", "ADJUSTER", now);
            user(users, credentials, encoder, "u-2002", "meera@example.com", "Meera", "SENIOR_ADJUSTER", now);
            user(users, credentials, encoder, "u-3001", "lee@example.com", "Lee", "MANAGER", now);

            Team team = new Team();
            team.setId("t-1");
            team.setName("SG Motor Adjusters");
            team.setMarketCode("SG");
            teams.save(team);

            member(members, "t-1", "u-2001");
            member(members, "t-1", "u-2002");
            member(members, "t-1", "u-3001");

            market(markets, "u-1001", "SG");
            market(markets, "u-2001", "SG");
            market(markets, "u-2002", "SG");
            market(markets, "u-2002", "MY");
            for (String code : new String[]{"SG", "MY", "HK", "AU", "GB", "AE"}) {
                market(markets, "u-3001", code);
            }

            policy(policies, "pol-77", "SG-123", "SG", "MOTOR", "ACTIVE",
                    LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                    new BigDecimal("500.00"), new BigDecimal("30000.00"), "SGD",
                    new String[]{"COLLISION", "THEFT", "FIRE"}, "u-1001", now);
            policy(policies, "pol-88", "SG-PROP-1", "SG", "PROPERTY", "ACTIVE",
                    LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"),
                    new BigDecimal("250.00"), new BigDecimal("150000.00"), "SGD",
                    new String[]{"WATER_DAMAGE", "FIRE", "THEFT"}, "u-1001", now);
            policy(policies, "pol-19", "SG-EXPIRED", "SG", "MOTOR", "EXPIRED",
                    LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"),
                    new BigDecimal("500.00"), new BigDecimal("30000.00"), "SGD",
                    new String[]{"COLLISION"}, "u-1001", now);
        };
    }

    private static void user(AppUserRepository users, UserCredentialRepository credentials, PasswordEncoder encoder,
                             String id, String email, String name, String role, Instant now) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(name);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(now);
        users.save(user);
        UserCredential credential = new UserCredential();
        credential.setUserId(id);
        credential.setPasswordHash(encoder.encode(PASSWORD));
        credentials.save(credential);
    }

    private static void member(TeamMemberRepository members, String teamId, String userId) {
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        members.save(member);
    }

    private static void market(UserMarketRepository markets, String userId, String market) {
        UserMarket row = new UserMarket();
        row.setUserId(userId);
        row.setMarketCode(market);
        markets.save(row);
    }

    private static void policy(PolicyRepository policies, String id, String number, String market, String product,
                               String status, LocalDate start, LocalDate end, BigDecimal excess, BigDecimal limit,
                               String currency, String[] perils, String insured, Instant now) {
        PolicySnapshot policy = new PolicySnapshot();
        policy.setPolicyId(id);
        policy.setPolicyNumber(number);
        policy.setMarketCode(market);
        policy.setProduct(product);
        policy.setStatus(status);
        policy.setCoverageStart(start);
        policy.setCoverageEnd(end);
        policy.setExcess(excess);
        policy.setCoverLimit(limit);
        policy.setCurrency(currency);
        policy.setCoveredPerils(perils);
        policy.setInsuredUserId(insured);
        policy.setFetchedAt(now);
        policies.save(policy);
    }
}
