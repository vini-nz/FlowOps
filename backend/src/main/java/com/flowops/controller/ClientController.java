package com.flowops.controller;

import com.flowops.dto.client.ClientRequest;
import com.flowops.dto.client.ClientResponse;
import com.flowops.entity.User;
import com.flowops.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public Page<ClientResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return clientService.list(user.getCompany().getId(), search, pageable);
    }

    @GetMapping("/{uuid}")
    public ClientResponse get(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        return clientService.get(user.getCompany().getId(), uuid);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientResponse create(@AuthenticationPrincipal User user, @Valid @RequestBody ClientRequest request) {
        return clientService.create(user.getCompany().getId(), request);
    }

    @PutMapping("/{uuid}")
    public ClientResponse update(
            @AuthenticationPrincipal User user,
            @PathVariable UUID uuid,
            @Valid @RequestBody ClientRequest request) {
        return clientService.update(user.getCompany().getId(), uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@AuthenticationPrincipal User user, @PathVariable UUID uuid) {
        clientService.deactivate(user.getCompany().getId(), uuid);
    }
}
