package io.github.poupeai.ingestion.service.impl;

import io.github.poupeai.ingestion.domain.exception.StorageException;
import io.github.poupeai.ingestion.service.StorageService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {
    private final MinioClient minioClient;

    @Value("${app.minio.bucket}")
    private String bucketName;

    @Override
    public InputStream downloadFile(String fileKey) {
        try {
            log.info("Iniciando download do arquivo: {} no bucket: {}", fileKey, bucketName);

            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileKey)
                            .build()
            );
        } catch (Exception e) {
            log.error("Erro ao baixar arquivo do MinIO: {}", fileKey, e);
            throw new StorageException("Falha ao baixar arquivo do storage: " + fileKey, e);
        }
    }
}
