package com.flowops.repository;

import com.flowops.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Login e feito por e-mail. Como o UNIQUE constraint real e (company_id, email),
    // um mesmo e-mail pode existir em empresas diferentes; para o MVP (login simples,
    // sem selecao de empresa na tela) assumimos e-mail unico por instalacao ativa.
    //
    // @EntityGraph forca o carregamento de "company" na MESMA query, via JOIN,
    // mesmo com company mapeado como LAZY na entidade User. Isso e necessario
    // porque este metodo e chamado de dois lugares com ciclos de vida de sessao
    // Hibernate diferentes:
    //   1) AuthService.login()          -> dentro de uma transacao (@Transactional),
    //      entao o LAZY funcionaria mesmo sem o EntityGraph.
    //   2) JwtAuthenticationFilter      -> FORA de qualquer transacao (filtro roda
    //      antes do Controller/Service). Sem o EntityGraph, a sessao Hibernate que
    //      carregou o User ja fecha antes do Controller acessar user.getCompany(),
    //      lancando LazyInitializationException (ver GlobalExceptionHandler: essa
    //      excecao era capturada por "catch (Exception ex)" e devolvida como 500,
    //      escondendo a causa real).
    @EntityGraph(attributePaths = "company")
    Optional<User> findByEmailAndActiveTrue(String email);
}

