package io.github.poupeai.ingestion.listener;

import io.github.poupeai.ingestion.domain.event.IngestionEvent;
import io.github.poupeai.ingestion.domain.model.BankTransaction;
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

    @RabbitListener(queues = "${app.rabbitmq.queue.ingestion}")
    public void handleIngestionEvent(IngestionEvent event) {
        log.info("Recebendo evento de ingestão. ID: {}", event.messageId());

        if (event.payload() == null) {
            log.warn("Payload nulo. Ignorando.");
            return;
        }

        String fileKey = event.payload().fileKey();
        log.info("Iniciando processamento do arquivo OFX: {}", fileKey);

        try (InputStream inputStream = storageService.downloadFile(fileKey)) {

            List<BankTransaction> transactions = ofxParserService.parse(inputStream);

            log.info("Sucesso! Parse OFX realizado. Foram encontradas {} transações válidas.", transactions.size());

           transactions.forEach(t -> log.info("Banco: {} | Data: {} | Valor: {} | FITID: {} | Desc: {}",
                    t.getBankCode(), t.getDate().toLocalDate(), t.getAmount(), t.getFitId(), t.getDescription()));

            // TODO: Próxima issue -> Enviar para Core Service

        } catch (Exception e) {
            log.error("Erro ao processar ingestão OFX", e);
        }
    }
}
