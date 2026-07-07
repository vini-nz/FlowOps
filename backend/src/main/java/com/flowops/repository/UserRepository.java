package com.flowops.repository;

import com.flowops.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Email e globalmente unico (UNIQUE(email) no DDL, corrigido em 6/jul/2026 -
    // ver CHANGELOG e docs/adr/0001-modelo-de-usuario.md). Antes disso o
    // constraint era (company_id, email), o que permitia o mesmo email em
    // empresas diferentes e quebrava esta query com
    // IncorrectResultSizeDataAccessException quando isso acontecia, pois
    // Optional<User> espera no maximo uma linha.
    //
    // @EntityGraph forca o carregamento de "company" na MESMA query, via JOIN,
    // mesmo com company mapeado como LAZY na entidade User. Necessario porque
    // este metodo tambem e chamado pelo JwtAuthenticationFilter, fora de
    // qualquer transacao.
    @EntityGraph(attributePaths = "company")
    Optional<User> findByEmailAndActiveTrue(String email);
}


