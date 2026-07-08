package com.flowops.controller;

import com.flowops.dto.user.UserOption;
import com.flowops.entity.User;
import com.flowops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint minimo: lista usuarios ativos da empresa do usuario autenticado,
 * usado pelo dropdown de "responsavel" no módulo de WorkOrders. Não é um
 * módulo de gestão de usuários completo (isso não está no escopo do MVP -
 * ver Roadmap e Entrega no Notion).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping
    public List<UserOption> list(@AuthenticationPrincipal User user) {
        return userRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(user.getCompany().getId())
                .stream()
                .map(UserOption::from)
                .toList();
    }
}
