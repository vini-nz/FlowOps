package com.flowops.repository;

import com.flowops.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    // Usado para atribuir responsavel a uma WorkOrder: o UUID vem do corpo da
    // requisicao (nunca confiavel por si so), entao o filtro por company_id
    // garante que so e possivel atribuir usuarios da mesma empresa (D-06).
    Optional<User> findByUuidAndCompanyIdAndActiveTrue(UUID uuid, Long companyId);

    // Alimenta o dropdown de "responsavel" no frontend (Clientes nao precisa
    // disso, mas WorkOrders sim).
    List<User> findByCompanyIdAndActiveTrueOrderByNameAsc(Long companyId);

    // Perfil (V2.8): ProfileResponse le company.name, e company e LAZY -
    // EntityGraph pelo mesmo motivo do metodo por email acima.
    @EntityGraph(attributePaths = "company")
    Optional<User> findWithCompanyById(Long id);

    // Checagem antes de trocar o e-mail, para devolver mensagem legivel em
    // vez de deixar a constraint UNIQUE(email) estourar.
    boolean existsByEmailIgnoreCase(String email);
}



