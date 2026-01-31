package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.domain.event.IngestionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IngestionListener {
    @RabbitListener(queues = "${app.rabbitmq.queue.ingestion}")
    public void handleIngestionEvent(IngestionEvent event) {
        log.info("Recebendo evento de ingestão. ID: {}, Type: {}", event.messageId(), event.eventType());

        if (event.payload() == null) {
            log.warn("Evento recebido sem payload. Ignorando.");
            return;
        }

        log.info("Iniciando processamento do Job ID: {} para o arquivo: {}",
                event.payload().jobId(), event.payload().fileKey());

        processIngestion(event);
    }

    private void processIngestion(IngestionEvent event) {
        log.info("Simulando processamento do arquivo...");
    }
}
