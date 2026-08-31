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
    public void sendWithActiveChat(Long operatorId, Long elderId, UniversalResponse response) {
        User recipient = userService.findByTelegramId(operatorId).orElse(null);
        if (recipient == null || recipient.getChatId() == null) {
            log.warn("⚠️ [MessageService] Не удалось отправить сообщение оператору {}", operatorId);
            return;
        }

        messageSender.sendMessage(recipient.getChatId(), response);
        log.info("📨 [MessageService] Сообщение отправлено оператору {}", operatorId);

        if (elderId != null) {
            userStateService.setActiveChatElder(operatorId, elderId);
            log.info("🔗 [MessageService] Активный чат для оператора {} установлен на заявку {}", operatorId, elderId);
        }
    }
}