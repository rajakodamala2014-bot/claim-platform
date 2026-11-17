package com.claims.platform.claims.platform.controller;

import com.claims.platform.claims.platform.dto.response.PersonResponse;
import com.claims.platform.claims.platform.service.PeopleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "People", description = "Names for the people already stored on a claim")
public class PeopleController {
    private final PeopleService people;

    public PeopleController(PeopleService people) {
        this.people = people;
    }

    @GetMapping("/staff/v1/people")
    @Operation(summary = "List people by the name shown on screen")
    public PeopleListResponse list() {
        return new PeopleListResponse(people.list());
    }

    public record PeopleListResponse(List<PersonResponse> items) {
    }
}
