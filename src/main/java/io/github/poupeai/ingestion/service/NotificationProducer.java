package io.github.poupeai.ingestion.service;

import io.github.poupeai.ingestion.domain.event.NotificationEvent;
import io.github.poupeai.ingestion.domain.event.NotificationPayload;
import io.github.poupeai.ingestion.domain.event.NotificationRecipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.notifications.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.notifications.routing-key}")
    private String routingKey;

    public void sendSuccess(String userId, String email, String name, String fileName, String accountName) {
        NotificationRecipient recipient = new NotificationRecipient(userId, email, name);
        NotificationPayload payload = NotificationPayload.success(fileName, accountName);

        NotificationEvent event = NotificationEvent.create(
                "STATEMENT_PROCESSING_COMPLETED",
                recipient,
                payload
        );

        publish(event);
    }

    public void sendError(String userId, String email, String name, String fileName, String accountName, String errorCode, String errorMessage) {
        NotificationRecipient recipient = new NotificationRecipient(userId, email, name);
        NotificationPayload payload = NotificationPayload.error(fileName, accountName, errorCode, errorMessage);

        NotificationEvent event = NotificationEvent.create(
                "STATEMENT_PROCESSING_FAILED",
                recipient,
                payload
        );

        publish(event);
    }

    private void publish(NotificationEvent event) {
        try {
            log.info("Enviando notificação: Type={} Recipient={}", event.eventType(), event.recipient().email());
            rabbitTemplate.convertAndSend(exchange, routingKey, event);
        } catch (Exception e) {
            log.error("Falha ao publicar evento de notificação", e);
        }
    }
}