package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.client.CoreServiceClient;
import io.github.poupeai.ingestion.client.ReportServiceClient;
import io.github.poupeai.ingestion.client.dto.CategorizationRequest;
import io.github.poupeai.ingestion.client.dto.CategorizationResponse;
import io.github.poupeai.ingestion.client.dto.CreateTransactionRequest;
import io.github.poupeai.ingestion.client.dto.TransactionType;
import io.github.poupeai.ingestion.client.dto.UpdateIngestionJobRequest;
import io.github.poupeai.ingestion.domain.event.IngestionEvent;
import io.github.poupeai.ingestion.domain.model.BankTransaction;
import io.github.poupeai.ingestion.dto.CategoryDTO;
import io.github.poupeai.ingestion.service.NotificationProducer;
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
    private final NotificationProducer notificationProducer;

    @RabbitListener(queues = "${app.rabbitmq.queue.ingestion}")
    public void handleIngestionEvent(IngestionEvent event) {
        log.info("Recebendo evento de ingestão. ID: {}", event.messageId());

        if (event.payload() == null) {
            log.warn("Payload nulo. Ignorando.");
            return;
        }

        String jobId = event.payload().jobId();
        String fileKey = event.payload().fileKey();

        String profileId = event.payload().profile().id();
        String profileName = event.payload().profile().name();
        String profileEmail = event.payload().profile().email();

        String bankAccountId = event.payload().bankAccount().id();
        String accountName = event.payload().bankAccount().name();

        String fallbackIncomeId = event.payload().fallbackIncomeCategoryId();
        String fallbackExpenseId = event.payload().fallbackExpenseCategoryId();

        String fileName = fileKey.contains("/") ? fileKey.substring(fileKey.lastIndexOf('/') + 1) : fileKey;

        log.info("Iniciando processamento. Arquivo: {}", fileKey);

        try {
            String initialSummary = """
                {
                    "message": "Iniciando download e leitura do arquivo...",
                    "step": "START"
                }
                """;
            updateJobStatus(jobId, "PROCESSING", initialSummary, null);

            try (InputStream inputStream = storageService.downloadFile(fileKey)) {

                List<BankTransaction> transactions = ofxParserService.parse(inputStream);
                log.info("Passo 1: OFX Parseado. {} transações encontradas.", transactions.size());

                if (transactions.isEmpty()) {
                    String emptySummary = """
                        {
                            "message": "Arquivo processado, mas nenhuma transação válida encontrada.",
                            "total_transactions": 0
                        }
                        """;
                    updateJobStatus(jobId, "COMPLETED", emptySummary, null);

                    notificationProducer.sendError(
                            profileId, profileEmail, profileName, fileName, accountName,
                            "NO_TRANSACTIONS", "Nenhuma transação encontrada no arquivo."
                    );
                    return;
                }

                List<CategoryDTO> userCategories = fetchCategoriesSafely(profileId);

                int categorizedByAi = 0;
                if (!userCategories.isEmpty()) {
                    categorizedByAi = applyCategorization(transactions, userCategories);
                }

                log.info("Passo 4: Enviando {} transações para persistência...", transactions.size());
                persistTransactionsBatch(transactions, profileId, bankAccountId, fallbackIncomeId, fallbackExpenseId);

                String summaryJson = String.format("""
                        {
                            "message": "Processamento concluído com sucesso.",
                            "total_transactions": %d,
                            "ai_categorized": %d
                        }
                        """, transactions.size(), categorizedByAi);

                updateJobStatus(jobId, "COMPLETED", summaryJson, null);
                log.info("Job {} finalizado com sucesso.", jobId);

                notificationProducer.sendSuccess(profileId, profileEmail, profileName, fileName, accountName);
            }

        } catch (Exception e) {
            log.error("Erro fatal ao processar Job {}", jobId, e);
            updateJobStatus(jobId, "FAILED", null, "Erro interno: " + e.getMessage());

            notificationProducer.sendError(
                    profileId, profileEmail, profileName, fileName, accountName,
                    "INTERNAL_ERROR", "Erro ao processar arquivo: " + e.getMessage()
            );
        }
    }

    private void updateJobStatus(String jobId, String status, String summary, String error) {
        try {
            if (jobId == null) return;
            coreServiceClient.updateStatus(jobId, UpdateIngestionJobRequest.builder()
                    .status(status)
                    .summary(summary)
                    .errorDetails(error)
                    .build());
        } catch (Exception e) {
            log.error("Falha ao atualizar status do job {}", jobId, e);
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

    private int applyCategorization(List<BankTransaction> transactions, List<CategoryDTO> userCategories) {
        log.info("Passo 3: Solicitando predição de categorias para IA...");
        int matches = 0;
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
        return matches;
    }

    private void persistTransactionsBatch(List<BankTransaction> transactions, String profileId, String bankAccountId,
                                          String fallbackIncomeId, String fallbackExpenseId) {

        List<CreateTransactionRequest> dtos = transactions.stream()
                .map(tx -> toCreateRequest(tx, profileId, bankAccountId, fallbackIncomeId, fallbackExpenseId))
                .toList();

        try {
            coreServiceClient.createTransactionsBatch(dtos);
        } catch (Exception e) {
            log.error("Erro ao salvar transações no Core Service.", e);
            throw e;
        }
    }

    private CreateTransactionRequest toCreateRequest(BankTransaction tx, String profileId, String bankAccountId,
                                                     String fallbackIncomeId, String fallbackExpenseId) {

        boolean isExpense = tx.getAmount().compareTo(BigDecimal.ZERO) < 0;
        TransactionType type = isExpense ? TransactionType.EXPENSE : TransactionType.INCOME;
        BigDecimal amountAbs = tx.getAmount().abs();

        String finalCategoryId = tx.getCategoryId();

        if (finalCategoryId == null) {
            finalCategoryId = isExpense ? fallbackExpenseId : fallbackIncomeId;
        }

        return new CreateTransactionRequest(
                UUID.fromString(profileId),
                UUID.fromString(bankAccountId),
                tx.getDescription(),
                amountAbs,
                type,
                tx.getDate().toLocalDate(),
                finalCategoryId != null ? UUID.fromString(finalCategoryId) : null,
                tx.getFitId()
        );
    }
}
