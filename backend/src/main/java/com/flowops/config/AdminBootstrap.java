package com.flowops.config;

import com.flowops.entity.Company;
import com.flowops.entity.User;
import com.flowops.enums.Role;
import com.flowops.repository.CompanyRepository;
import com.flowops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Cria a primeira empresa e o primeiro administrador quando o banco está
 * vazio (V3.0).
 * <p>
 * Existe por um motivo prático descoberto ao simular o deploy: em produção o
 * seed de demonstração não roda (de propósito), então o banco sobe com zero
 * usuários — a aplicação responde {@code UP}, mas <b>ninguém consegue
 * entrar</b>. Sem isto, a única saída seria rodar INSERT na mão no painel do
 * provedor, com um hash bcrypt gerado à parte.
 * <p>
 * Três garantias para não virar um risco:
 * <ul>
 *   <li>Só age se as variáveis estiverem definidas — sem elas, não faz nada.</li>
 *   <li>Só age se <b>não existir nenhum usuário</b>. Não é "criar admin", é
 *       "semear um sistema vazio": rodar de novo depois não faz efeito.</li>
 *   <li>A senha vem de variável de ambiente e nunca é registrada em log.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${flowops.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${flowops.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${flowops.bootstrap.admin-name:Administrador}")
    private String adminName;

    @Value("${flowops.bootstrap.company-name:Minha Empresa}")
    private String companyName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(adminEmail) || !StringUtils.hasText(adminPassword)) {
            return;
        }
        if (userRepository.count() > 0) {
            log.debug("Bootstrap ignorado: o sistema já possui usuários");
            return;
        }

        Company company = new Company();
        company.setName(companyName);
        company.setSlug(slugify(companyName));
        company.setEmail(adminEmail);
        Company savedCompany = companyRepository.save(company);

        User admin = new User();
        admin.setCompany(savedCompany);
        admin.setName(adminName);
        admin.setEmail(adminEmail.trim().toLowerCase());
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN_EMPRESA);
        userRepository.save(admin);

        // Nunca logar a senha - o log do provedor costuma ser retido e
        // visivel para qualquer pessoa com acesso ao painel.
        log.info("Sistema vazio: empresa '{}' e administrador '{}' criados no bootstrap",
                savedCompany.getName(), admin.getEmail());
    }

    private String slugify(String name) {
        String slug = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return StringUtils.hasText(slug) ? slug : "empresa";
    }
}
