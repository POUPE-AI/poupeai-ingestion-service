package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.domain.event.IngestionEvent;
import io.github.poupeai.ingestion.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionListener {

    private final StorageService storageService;

    @RabbitListener(queues = "${app.rabbitmq.queue.ingestion}")
    public void handleIngestionEvent(IngestionEvent event) {
        log.info("Recebendo evento de ingestão. ID: {}, Type: {}", event.messageId(), event.eventType());

        if (event.payload() == null) {
            log.warn("Evento recebido sem payload. Ignorando.");
            return;
        }

        String fileKey = event.payload().fileKey();
        log.info("Tentando processar arquivo: {}", fileKey);

        try (InputStream inputStream = storageService.downloadFile(fileKey)) {
            log.info("SUCESSO! Arquivo acessado. Bytes disponíveis estimados: {}", inputStream.available());


        } catch (Exception e) {
            log.error("Erro ao processar ingestão", e);
        }
    }
}
