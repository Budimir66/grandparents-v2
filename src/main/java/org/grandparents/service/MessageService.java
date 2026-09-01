package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.Elder;
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
    private final ElderService elderService;

    public MessageService(UserService userService,
                          UserStateService userStateService,
                          MessageSender messageSender,
                          ElderService elderService) {
        this.userService = userService;
        this.userStateService = userStateService;
        this.messageSender = messageSender;
        this.elderService = elderService;
    }

    /**
     * Отправляет сообщение получателю и устанавливает активный чат для оператора
     */
    public void sendWithActiveChat(Long senderId, Long recipientId, Long elderId, UniversalResponse response) {
        // 1. Получаем получателя
        User recipient = userService.findByTelegramId(recipientId).orElse(null);
        if (recipient == null || recipient.getChatId() == null) {
            log.warn("⚠️ [MessageService] Не удалось отправить сообщение получателю {}", recipientId);
            return;
        }

        // 2. Получаем заявку
        Elder elder = elderService.findById(elderId);
        if (elder == null) {
            log.warn("⚠️ [MessageService] Заявка {} не найдена", elderId);
            return;
        }

        // 3. Сохраняем последнего получателя
        elder.setLastRecipientId(recipientId);  // ← ТЕПЕРЬ ЭТОТ МЕТОД СУЩЕСТВУЕТ!
        elderService.updateElder(elder);
        log.info("📝 [MessageService] Последний получатель для заявки {} установлен как {}", elderId, recipientId);

        // 4. Отправляем сообщение
        messageSender.sendMessage(recipient.getChatId(), response);
        log.info("📨 [MessageService] Сообщение отправлено в чат {}", recipient.getChatId());

        // 5. Активный чат для отправителя
        if (elderId != null) {
            userStateService.setActiveChatElder(senderId, elderId);
            log.info("🔗 [MessageService] Активный чат для отправителя {} установлен на заявку {}", senderId, elderId);
        }

        // 6. Активный чат для получателя
        if (elderId != null) {
            userStateService.setActiveChatElder(recipientId, elderId);
            log.info("🔗 [MessageService] Активный чат для получателя {} установлен на заявку {}", recipientId, elderId);
        }

        // 7. Сохраняем последнего отправителя (опционально)
        elder.setLastSenderId(senderId);
        elderService.updateElder(elder);
        log.info("📝 [MessageService] Последний отправитель для заявки {} установлен как {}", elderId, senderId);
    }
}