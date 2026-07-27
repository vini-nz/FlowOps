package com.flowops.service;

import com.flowops.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Optional;

/**
 * Encapsula o storage S3-compatível (V2.6). Isolado do resto dos serviços de
 * propósito: nenhuma regra de negócio deveria conhecer a API da AWS, e trocar
 * MinIO por S3/R2 não pode vazar para o domínio.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    /** URL de escrita: o navegador faz PUT direto aqui, sem passar pela API. */
    public String presignUpload(String objectKey, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        return s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(properties.getUploadUrlExpirationMinutes()))
                        .putObjectRequest(putRequest)
                        .build())
                .url()
                .toString();
    }

    /**
     * URL de leitura de curta duração. É o que mantém o bucket privado: nada
     * é servido publicamente, cada acesso passa por uma URL assinada que o
     * backend só emite depois de checar o isolamento por empresa.
     */
    public String presignDownload(String objectKey, String fileName) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                // Faz o navegador baixar com o nome original em vez da chave.
                .responseContentDisposition("attachment; filename=\"%s\"".formatted(fileName))
                .build();

        return s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(properties.getDownloadUrlExpirationMinutes()))
                        .getObjectRequest(getRequest)
                        .build())
                .url()
                .toString();
    }

    /**
     * Confirma que o objeto existe de fato. Sem esta checagem, um cliente
     * poderia marcar como enviada uma evidência que nunca chegou ao storage —
     * a galeria mostraria um arquivo que quebra ao ser baixado.
     */
    public Optional<Long> findObjectSize(String objectKey) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
            return Optional.of(head.contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .build());
    }
}
