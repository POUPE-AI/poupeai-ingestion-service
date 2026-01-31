package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.client.CoreServiceClient;
import io.github.poupeai.ingestion.domain.event.IngestionEvent;
import io.github.poupeai.ingestion.domain.model.BankTransaction;
import io.github.poupeai.ingestion.dto.CategoryDTO;
import io.github.poupeai.ingestion.service.StorageService;
import io.github.poupeai.ingestion.service.parser.OfxParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionListener {

    private final StorageService storageService;
    private final OfxParserService ofxParserService;
    private final CoreServiceClient coreServiceClient;

    @RabbitListener(queues = "${app.rabbitmq.queue.ingestion}")
    public void handleIngestionEvent(IngestionEvent event) {
        log.info("Recebendo evento de ingestão. ID: {}", event.messageId());

        if (event.payload() == null) {
            log.warn("Payload nulo. Ignorando.");
            return;
        }

        String fileKey = event.payload().fileKey();
        String profileId = event.payload().profileId();

        log.info("Iniciando processamento. Arquivo: {} | Profile: {}", fileKey, profileId);

        try (InputStream inputStream = storageService.downloadFile(fileKey)) {

            List<BankTransaction> transactions = ofxParserService.parse(inputStream);
            log.info("OFX Parseado: {} transações encontradas.", transactions.size());

            log.info("Buscando categorias do usuário no Core Service...");
            try {
                List<CategoryDTO> categories = coreServiceClient.getCategories(profileId);

                log.info("SUCESSO! Categorias recuperadas: {}", categories.size());
                categories.forEach(c -> log.info(" - Categoria: {} ({})", c.name(), c.id()));

                // TODO: Na próxima issue, enviaremos (Transações + Categorias) para a IA

            } catch (Exception e) {
                log.error("Erro ao buscar categorias no Core Service. Verifique se o serviço está UP e a API Key está correta.", e);
            }

        } catch (Exception e) {
            log.error("Erro ao processar ingestão", e);
        }
    }
}
