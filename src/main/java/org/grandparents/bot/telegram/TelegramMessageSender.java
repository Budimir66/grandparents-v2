package org.grandparents.bot.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.service.MessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TelegramMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramMessageSender.class);

    private final String botToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramMessageSender(@Value("${telegram.bot.token}") String botToken) {
        this.botToken = botToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
        log.info("✅ TelegramMessageSender создан");
    }

    @Override
    public void sendMessage(Long chatId, UniversalResponse response) {
        if (response == null || response.getText() == null) {
            log.warn("⚠️ [Telegram] Нет ответа для отправки");
            return;
        }

        try {
            String url = "https://api.telegram.org/bot" + botToken + "/sendMessage";

            // Создаём тело запроса
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId.toString());
            body.put("text", response.getText());
            body.put("parse_mode", "HTML");

            // Добавляем кнопки, если есть
            if (response.getButtons() != null && !response.getButtons().isEmpty()) {
                Map<String, Object> replyMarkup = buildKeyboard(response);
                body.put("reply_markup", replyMarkup);
            }

            String jsonBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                log.info("✅ [Telegram] Сообщение отправлено в чат {}", chatId);
            } else {
                log.error("❌ [Telegram] Ошибка отправки: {} - {}", httpResponse.statusCode(), httpResponse.body());
            }

        } catch (Exception e) {
            log.error("❌ [Telegram] Ошибка отправки: {}", e.getMessage(), e);
        }
    }

    /**
     * Создаёт клавиатуру для Telegram
     */
    private Map<String, Object> buildKeyboard(UniversalResponse response) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> currentRow = new ArrayList<>();

        for (UniversalResponse.Button btn : response.getButtons()) {
            Map<String, Object> button = new HashMap<>();
            button.put("text", btn.getText());
            button.put("callback_data", btn.getCallbackData());

            if (btn.isFullRow()) {
                if (!currentRow.isEmpty()) {
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                }
                List<Map<String, Object>> fullRow = new ArrayList<>();
                fullRow.add(button);
                rows.add(fullRow);
            } else {
                if (currentRow.size() == 2) {
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                }
                currentRow.add(button);
            }
        }

        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }

        Map<String, Object> keyboard = new HashMap<>();
        keyboard.put("inline_keyboard", rows);
        return keyboard;
    }
}