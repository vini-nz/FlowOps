package com.flowops.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Dois clientes S3 apontando para endpoints diferentes (V2.6):
 * <ul>
 *   <li>{@link S3Client} → endpoint interno, usado pelo backend para criar o
 *       bucket, checar se o objeto existe e excluir.</li>
 *   <li>{@link S3Presigner} → endpoint público, usado só para gerar as URLs
 *       que o navegador vai chamar.</li>
 * </ul>
 * Rodando via Docker os dois são diferentes ({@code storage:9000} vs
 * {@code localhost:9000}) e é isso que faz a URL pré-assinada funcionar de
 * fato no browser — a assinatura SigV4 cobre o Host, então assinar com o
 * nome interno geraria uma URL inútil fora da rede do compose.
 * <p>
 * {@code pathStyleAccessEnabled} é obrigatório para MinIO, que não usa o
 * esquema {@code bucket.host} da AWS.
 */
@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private final StorageProperties properties;

    @Bean
    public S3Client s3Client() {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .credentialsProvider(credentials())
                .region(Region.US_EAST_1) // MinIO ignora, mas o SDK exige uma
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        ensureBucketExists(client);
        return client;
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getPublicEndpoint()))
                .credentialsProvider(credentials())
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }

    // Cria o bucket na subida se ainda nao existir: sem isso o primeiro
    // upload de uma instalacao nova falharia, e a correcao seria manual no
    // console do MinIO - exatamente o tipo de passo escondido que quebra um
    // "docker compose up" limpo.
    private void ensureBucketExists(S3Client client) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (NoSuchBucketException e) {
            log.info("Bucket '{}' nao existe, criando", properties.getBucket());
            client.createBucket(CreateBucketRequest.builder().bucket(properties.getBucket()).build());
        } catch (Exception e) {
            // Nao derruba a aplicacao: o resto do sistema funciona sem
            // evidencias, e um storage indisponivel no boot nao deveria
            // impedir login, orcamento ou etapas.
            log.warn("Nao foi possivel verificar/criar o bucket '{}': {}",
                    properties.getBucket(), e.getMessage());
        }
    }
}
