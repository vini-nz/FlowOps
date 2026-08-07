package com.flowops.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "flowops.storage")
@Getter
@Setter
public class StorageProperties {

    /** Endpoint usado pelo backend (rede interna, ex: http://storage:9000). */
    private String endpoint;

    /**
     * Endpoint para o qual as URLs pré-assinadas são assinadas. Precisa ser o
     * host que o navegador alcança: a assinatura AWS SigV4 cobre o cabeçalho
     * Host, então assinar com o nome interno do container produziria uma URL
     * que o browser nem resolve e cuja assinatura não confere.
     */
    private String publicEndpoint;

    private String bucket;
    private String accessKey;
    private String secretKey;

    /**
     * Região usada para assinar as requisições. O MinIO local ignora, mas
     * provedores reais não: a assinatura SigV4 inclui a região, então assinar
     * com a região errada produz um erro de credencial inválida — enganoso,
     * porque a credencial está correta.
     */
    private String region = "us-east-1";

    private int uploadUrlExpirationMinutes = 10;
    private int downloadUrlExpirationMinutes = 15;
    private int maxFileSizeMb = 15;
}
