package com.claims.platform.claims.platform.service;

import com.claims.platform.claims.platform.dto.response.PersonResponse;
import com.claims.platform.claims.platform.repository.AppUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PeopleService {
    private final AppUserRepository users;

    public PeopleService(AppUserRepository users) {
        this.users = users;
    }

    public List<PersonResponse> list() {
        return users.list().stream()
                .filter(user -> user.enabled())
                .map(user -> new PersonResponse(user.id(), PersonNames.of(user.username()), user.role()))
                .toList();
    }
}
