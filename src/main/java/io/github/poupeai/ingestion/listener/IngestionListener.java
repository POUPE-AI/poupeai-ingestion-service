package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.client.CoreServiceClient;
import io.github.poupeai.ingestion.client.ReportServiceClient;
import io.github.poupeai.ingestion.client.dto.CategorizationRequest;
import io.github.poupeai.ingestion.client.dto.CategorizationResponse;
import io.github.poupeai.ingestion.client.dto.CreateTransactionRequest;
import io.github.poupeai.ingestion.client.dto.TransactionType;
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
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        String bankAccountId = event.payload().bankAccountId();

        log.info("Iniciando processamento. Arquivo: {} | Profile: {}", fileKey, profileId);

        try (InputStream inputStream = storageService.downloadFile(fileKey)) {

            List<BankTransaction> transactions = ofxParserService.parse(inputStream);
            log.info("Passo 1: OFX Parseado. {} transações encontradas.", transactions.size());

            if (transactions.isEmpty()) {
                log.warn("Nenhuma transação válida encontrada no arquivo.");
                return;
            }

            List<CategoryDTO> userCategories = fetchCategoriesSafely(profileId);

            if (!userCategories.isEmpty()) {
                applyCategorization(transactions, userCategories);
            }

            log.info("Passo 4: Enviando {} transações para persistência no Core...", transactions.size());
            persistTransactionsBatch(transactions, profileId, bankAccountId);

        } catch (Exception e) {
            log.error("Erro fatal ao processar ingestão", e);
        }
    }

    private List<CategoryDTO> fetchCategoriesSafely(String profileId) {
        try {
            return coreServiceClient.getCategories(profileId);
        } catch (Exception e) {
            log.error("Falha não-bloqueante ao buscar categorias: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void applyCategorization(List<BankTransaction> transactions, List<CategoryDTO> userCategories) {
        log.info("Passo 3: Solicitando predição de categorias para IA...");
        try {
            List<String> descriptions = transactions.stream()
                    .map(BankTransaction::getDescription)
                    .distinct()
                    .toList();

            CategorizationRequest request = new CategorizationRequest(descriptions, userCategories);
            CategorizationResponse response = reportServiceClient.predictCategories(request);

            List<CategorizationResponse.CategorizationItem> items = response != null ? response.getCategorizationsSafe() : Collections.emptyList();

            if (!items.isEmpty()) {
                Map<String, String> predictedMap = items.stream()
                        .filter(item -> item.categoryId() != null)
                        .collect(Collectors.toMap(
                                CategorizationResponse.CategorizationItem::description,
                                CategorizationResponse.CategorizationItem::categoryId,
                                (existing, replacement) -> existing
                        ));

                int matches = 0;
                for (BankTransaction tx : transactions) {
                    String catId = predictedMap.get(tx.getDescription());
                    if (catId != null) {
                        tx.setCategoryId(catId);
                        matches++;
                    }
                }
                log.info("IA Categorizou {} de {} transações.", matches, transactions.size());
            }
        } catch (Exception e) {
            log.error("Erro na integração com IA (Report Service): {}", e.getMessage());
        }
    }

    private void persistTransactionsBatch(List<BankTransaction> transactions, String profileId, String bankAccountId) {
        List<CreateTransactionRequest> dtos = transactions.stream()
                .map(tx -> toCreateRequest(tx, profileId, bankAccountId))
                .toList();

        try {
            coreServiceClient.createTransactionsBatch(dtos);
            log.info("SUCESSO FINAL! Transações salvas no Core Service.");
        } catch (Exception e) {
            log.error("Erro ao salvar transações no Core Service.", e);
            throw e;
        }
    }

    private CreateTransactionRequest toCreateRequest(BankTransaction tx, String profileId, String bankAccountId) {
        boolean isExpense = tx.getAmount().compareTo(BigDecimal.ZERO) < 0;
        TransactionType type = isExpense ? TransactionType.EXPENSE : TransactionType.INCOME;

        BigDecimal amountAbs = tx.getAmount().abs();

        UUID categoryId = tx.getCategoryId() != null ? UUID.fromString(tx.getCategoryId()) : null;

        return new CreateTransactionRequest(
                UUID.fromString(profileId),
                UUID.fromString(bankAccountId),
                tx.getDescription(),
                amountAbs,
                type,
                tx.getDate().toLocalDate(),
                categoryId,
                tx.getFitId()
        );
    }
}
