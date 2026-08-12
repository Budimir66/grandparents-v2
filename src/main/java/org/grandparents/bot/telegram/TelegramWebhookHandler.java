package org.grandparents.bot.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.service.BotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/telegram")
public class TelegramWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookHandler.class);

    private final BotService botService;
    private final TelegramMessageSender messageSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramWebhookHandler(BotService botService,
                                  TelegramMessageSender messageSender) {
        this.botService = botService;
        this.messageSender = messageSender;
    }

    @PostMapping
    public String handleWebhook(@RequestBody String body) {
        log.info("📩 [Telegram] Получен запрос");
        log.info("📩 Body: {}", body);

        try {
            JsonNode root = objectMapper.readTree(body);

            // ===== ОБРАБОТКА CALLBACK (нажатие на кнопку) =====
            if (root.has("callback_query")) {
                JsonNode callback = root.path("callback_query");
                JsonNode from = callback.path("from");
                JsonNode message = callback.path("message");
                JsonNode chat = message.path("chat");

                String userId = from.path("id").asText();
                String chatId = chat.path("id").asText();
                String callbackData = callback.path("data").asText(null);

                log.info("📥 [Telegram] CALLBACK: userId={}, chatId={}, data={}", userId, chatId, callbackData);

                UniversalMessage universalMessage = new UniversalMessage();
                universalMessage.setPlatform("TELEGRAM");
                universalMessage.setUserId(userId);
                universalMessage.setChatId(chatId);
                universalMessage.setText("");
                universalMessage.setCallbackData(callbackData);

                UniversalResponse response = botService.handleMessage(universalMessage);
                if (response != null) {
                    messageSender.sendMessage(Long.parseLong(chatId), response);
                }

                return "OK";
            }

            // ===== ОБРАБОТКА ОБЫЧНОГО СООБЩЕНИЯ =====
            if (!root.has("message")) {
                log.warn("⚠️ Не сообщение, игнорируем");
                return "OK";
            }

            JsonNode message = root.path("message");
            JsonNode chat = message.path("chat");
            JsonNode from = message.path("from");

            String userId = from.path("id").asText();
            String chatId = chat.path("id").asText();
            String text = message.path("text").asText(null);

            log.info("📥 [Telegram] MESSAGE: userId={}, chatId={}, text={}", userId, chatId, text);

            UniversalMessage universalMessage = new UniversalMessage();
            universalMessage.setPlatform("TELEGRAM");
            universalMessage.setUserId(userId);
            universalMessage.setChatId(chatId);
            universalMessage.setText(text != null ? text : "");
            universalMessage.setCallbackData(null);

            UniversalResponse response = botService.handleMessage(universalMessage);

            if (response != null) {
                messageSender.sendMessage(Long.parseLong(chatId), response);
            }

            return "OK";

        } catch (Exception e) {
            log.error("❌ Ошибка обработки вебхука Telegram: {}", e.getMessage(), e);
            return "ERROR";
        }
    }
}