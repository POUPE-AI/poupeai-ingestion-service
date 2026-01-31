package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.client.CoreServiceClient;
import io.github.poupeai.ingestion.client.ReportServiceClient;
import io.github.poupeai.ingestion.client.dto.CategorizationRequest;
import io.github.poupeai.ingestion.client.dto.CategorizationResponse;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionListener {

    private final StorageService storageService;
    private final OfxParserService ofxParserService;
    private final CoreServiceClient coreServiceClient;
    private final ReportServiceClient reportServiceClient;

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
            log.info("Passo 1: OFX Parseado. {} transações.", transactions.size());

            List<CategoryDTO> userCategories = Collections.emptyList();
            try {
                userCategories = coreServiceClient.getCategories(profileId);
                log.info("Passo 2: Categorias recuperadas: {}", userCategories.size());
            } catch (Exception e) {
                log.error("Falha ao buscar categorias. A categorização IA será prejudicada.", e);
            }

            if (!userCategories.isEmpty() && !transactions.isEmpty()) {
                applyCategorization(transactions, userCategories);
            } else {
                log.warn("Pulando etapa de IA (Categorias ou Transações vazias).");
            }

            transactions.forEach(t -> log.info("TX: {} | Valor: {} | CatID: {} | Desc: {}",
                    t.getDate().toLocalDate(), t.getAmount(), t.getCategoryId(), t.getDescription()));

            // TODO: Próxima issue -> Persistir (POST /transactions) no Core

        } catch (Exception e) {
            log.error("Erro ao processar ingestão", e);
        }
    }

    private void applyCategorization(List<BankTransaction> transactions, List<CategoryDTO> userCategories) {
        log.info("Passo 3: Solicitando predição de categorias para IA...");
        try {
            List<String> descriptions = transactions.stream()
                    .map(BankTransaction::getDescription)
                    .toList();

            CategorizationRequest request = new CategorizationRequest(descriptions, userCategories);
            CategorizationResponse response = reportServiceClient.predictCategories(request);

            if (response != null && response.categorizations() != null) {
                Map<String, String> predictedMap = response.categorizations().stream()
                        .collect(Collectors.toMap(
                                CategorizationResponse.CategorizationItem::description,
                                CategorizationResponse.CategorizationItem::categoryId,
                                (existing, replacement) -> existing
                        ));

                int matches = 0;
                for (BankTransaction tx : transactions) {
                    String predictedCatId = predictedMap.get(tx.getDescription());
                    if (predictedCatId != null) {
                        tx.setCategoryId(predictedCatId);
                        matches++;
                    }
                }
                log.info("IA Categorizou {} de {} transações.", matches, transactions.size());
            }

        } catch (Exception e) {
            log.error("Erro ao chamar serviço de categorização (IA)", e);
        }
    }
}
