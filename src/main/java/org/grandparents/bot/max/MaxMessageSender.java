package org.grandparents.bot.max;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.service.MessageSender;
import org.springframework.context.annotation.Lazy;  // ← ДОБАВИТЬ
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class MaxMessageSender implements MessageSender {

    private final MaxWebhookHandler maxWebhookHandler;

    // ← ДОБАВЛЯЕМ @Lazy В КОНСТРУКТОР
    public MaxMessageSender(@Lazy MaxWebhookHandler maxWebhookHandler) {
        this.maxWebhookHandler = maxWebhookHandler;
    }

    @Override
    public void sendMessage(Long chatId, UniversalResponse response) {
        maxWebhookHandler.sendResponse(chatId, response);
    }
}