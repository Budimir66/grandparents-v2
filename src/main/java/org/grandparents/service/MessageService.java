package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final UserService userService;
    private final UserStateService userStateService;
    private final MessageSender messageSender;

    public MessageService(UserService userService,
                          UserStateService userStateService,
                          MessageSender messageSender) {
        this.userService = userService;
        this.userStateService = userStateService;
        this.messageSender = messageSender;
    }

    /**
     * Отправляет сообщение получателю и устанавливает активный чат для оператора
     */
    public void sendWithActiveChat(Long senderId, Long recipientId, Long elderId, UniversalResponse response) {
        // 1. Отправляем сообщение получателю
        User recipient = userService.findByTelegramId(recipientId).orElse(null);
        if (recipient == null || recipient.getChatId() == null) {
            log.warn("⚠️ [MessageService] Не удалось отправить сообщение получателю {}", recipientId);
            return;
        }

        messageSender.sendMessage(recipient.getChatId(), response);
        log.info("📨 [MessageService] Сообщение отправлено получателю {}", recipientId);

        // 2. Устанавливаем активный чат ДЛЯ ОТПРАВИТЕЛЯ (оператора)
        if (elderId != null) {
            userStateService.setActiveChatElder(senderId, elderId);
            log.info("🔗 [MessageService] Активный чат для отправителя {} установлен на заявку {}", senderId, elderId);
        }

        // 3. Устанавливаем активный чат ДЛЯ ПОЛУЧАТЕЛЯ (чтобы он мог ответить)
        if (elderId != null) {
            userStateService.setActiveChatElder(recipientId, elderId);
            log.info("🔗 [MessageService] Активный чат для получателя {} установлен на заявку {}", recipientId, elderId);
        }
    }
}