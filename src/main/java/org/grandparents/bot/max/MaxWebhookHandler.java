package org.grandparents.bot.max;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.CareHome;
import org.grandparents.model.User;
import org.grandparents.service.BotService;
import org.grandparents.service.ElderService;
import org.grandparents.service.UserService;
import org.grandparents.service.CareHomeService;
import org.grandparents.service.UserStateService;
import org.grandparents.statemachine.DialogState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.max.botapi.client.JdkHttpMaxTransportClient;
import ru.max.botapi.client.MaxBotAPI;
import ru.max.botapi.client.MaxClient;
import ru.max.botapi.client.MaxClientConfig;
import ru.max.botapi.jackson.JacksonMaxSerializer;
import ru.max.botapi.model.NewMessageBody;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@RestController
@RequestMapping("/webhook/max")
@Component
public class MaxWebhookHandler {
    private static final Logger log = LoggerFactory.getLogger(MaxWebhookHandler.class);

    private final BotService botService;
    private final ElderService elderService;
    private final UserService userService;
    private final CareHomeService careHomeService;
    private final UserStateService stateService;
    private final String maxToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MaxBotAPI api;

    public MaxWebhookHandler(BotService botService,
                             ElderService elderService,
                             UserService userService,
                             CareHomeService careHomeService,
                             UserStateService stateService,
                             @Value("${max.bot.webhook.token}") String maxToken) {
        this.botService = botService;
        this.elderService = elderService;
        this.userService = userService;
        this.careHomeService = careHomeService;
        this.stateService = stateService;
        this.maxToken = maxToken;
        log.info("✅ MAX Webhook Handler создан!");
    }

    @PostConstruct
    public void init() throws Exception {
        MaxClientConfig config = MaxClientConfig.builder()
                .baseUrl("https://platform-api2.max.ru")
                .connectTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(60))
                .build();

        JdkHttpMaxTransportClient transport = new JdkHttpMaxTransportClient(maxToken, config);
        JacksonMaxSerializer serializer = new JacksonMaxSerializer();
        MaxClient client = new MaxClient(transport, serializer, config);
        this.api = new MaxBotAPI(client);
        log.info("✅ MAX Bot API создан!");
    }

    @PostMapping
    public String handleWebhook(@RequestBody String body, HttpServletRequest request) {
        log.info("📩 ===== ВХОДЯЩИЙ ЗАПРОС ОТ MAX =====");
        log.info("📩 Body: " + body);

        try {
            JsonNode root = objectMapper.readTree(body);
            String updateType = root.path("update_type").asText();
            log.info("📌 update_type: " + updateType);

            // ===== ЭТО CALLBACK ОТ КНОПКИ =====
            if ("message_callback".equals(updateType)) {
                JsonNode callbackNode = root.path("callback");
                String callbackData = callbackNode.path("payload").asText(null);
                JsonNode userNode = callbackNode.path("user");
                String userId = userNode.path("user_id").asText(null);

                JsonNode messageNode = root.path("message");
                JsonNode recipientNode = messageNode.path("recipient");
                String chatId = recipientNode.path("chat_id").asText(null);

                // ===== ПОЛУЧАЕМ ID СООБЩЕНИЯ =====
                String messageId = messageNode.path("body").path("mid").asText(null);

                log.info("📥 [CALLBACK] userId: " + userId);
                log.info("📥 [CALLBACK] chatId: " + chatId);
                log.info("📥 [CALLBACK] callbackData: " + callbackData);
                log.info("📥 [CALLBACK] messageId: " + messageId);


                if (callbackData == null || userId == null) {
                    log.info("⚠️ Нет callback данных или userId");
                    return "OK";
                }

                UniversalMessage message = new UniversalMessage();
                message.setPlatform("MAX");
                message.setUserId(userId);
                message.setChatId(chatId);
                message.setText("");
                message.setCallbackData(callbackData);

                UniversalResponse response = botService.handleMessage(message);
                if (response != null) {
                    sendResponse(Long.parseLong(chatId), response);
                }
                return "OK";
            }

            // ===== ЭТО ОБЫЧНОЕ СООБЩЕНИЕ =====
            JsonNode messageNode = root.path("message");
            JsonNode senderNode = messageNode.path("sender");
            JsonNode recipientNode = messageNode.path("recipient");
            JsonNode bodyNode = messageNode.path("body");

            String userId = senderNode.path("user_id").asText(null);
            String chatId = recipientNode.path("chat_id").asText(null);
            String text = bodyNode.path("text").asText(null);

            // ===== ПОЛУЧАЕМ ID СООБЩЕНИЯ =====
            String messageId = bodyNode.path("mid").asText(null);

            log.info("📥 [MESSAGE] userId: " + userId);
            log.info("📥 [MESSAGE] chatId: " + chatId);
            log.info("📥 [MESSAGE] text: " + text);
            log.info("📥 [MESSAGE] messageId: " + messageId);

            log.info("📥 [MESSAGE] userId: " + userId);
            log.info("📥 [MESSAGE] chatId: " + chatId);
            log.info("📥 [MESSAGE] text: " + text);


            if (userId == null || chatId == null) {
                log.info("⚠️ Нет userId или chatId");
                return "OK";
            }

            // ===== ПРОВЕРЯЕМ, ЭТО КОНТАКТ? (В ATTACHMENTS) =====
            JsonNode attachmentsNode = bodyNode.path("attachments");
            String vcf = null;

            if (attachmentsNode.isArray() && attachmentsNode.size() > 0) {
                for (JsonNode attachment : attachmentsNode) {
                    String type = attachment.path("type").asText();
                    if ("contact".equals(type)) {
                        JsonNode payload = attachment.path("payload");
                        vcf = payload.path("vcf_info").asText(null);
                        if (vcf != null) {
                            log.info("📱 Найден контакт в attachments");
                            break;
                        }
                    }
                }
            }

            if (vcf != null) {
                log.info("📱 Получен контакт от пользователя {}", userId);
                handleContactFromMax(Long.parseLong(userId), vcf);
                return "OK";
            }

            // ===== СОХРАНЯЕМ CHAT_ID В БАЗУ =====
            // ===== СОХРАНЯЕМ CHAT_ID В БАЗУ =====
            // ===== СОХРАНЯЕМ CHAT_ID В БАЗУ (ТОЛЬКО ДЛЯ ГОСТЕЙ) =====
            try {
                User user = userService.findByTelegramId(Long.parseLong(userId)).orElse(null);
                if (user != null && user.getAccessLevel() == AccessLevel.GUEST) {
                    // Сохраняем chat_id только если пользователь — гость и ещё не зарегистрирован
                    if (user.getChatId() == null) {
                        user.setChatId(Long.parseLong(chatId));
                        userService.saveUser(user);
                        log.info("✅ Сохранен chat_id=" + chatId + " для нового пользователя " + userId);
                    }
                } else if (user != null) {
                    log.info("ℹ️ Пользователь {} уже зарегистрирован (accessLevel={}), chat_id не обновляется", userId, user.getAccessLevel());
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка сохранения chat_id: " + e.getMessage());
            }

            UniversalMessage message = new UniversalMessage();
            message.setPlatform("MAX");
            message.setUserId(userId);
            message.setChatId(chatId);
            message.setText(text != null ? text : "");
            message.setCallbackData(null);

            UniversalResponse response = botService.handleMessage(message);
            if (response != null) {
                sendResponse(Long.parseLong(chatId), response);
            }

            return "OK";

        } catch (Exception e) {
            System.err.println("❌ Ошибка обработки вебхука: " + e.getMessage());
            e.printStackTrace();
            return "ERROR";
        }
    }

    // ===== ОТПРАВКА ОТВЕТА =====
    // ===== ОТПРАВКА ОТВЕТА =====
    @Async
    public void sendResponse(Long chatId, UniversalResponse response) {
        if (response == null || response.getText() == null) {
            log.warn("⚠️ Нет ответа для отправки");
            return;
        }

        try {
            log.info("📤 Отправляем пользователю: {}", chatId);

            // ===== БАЗОВОЕ СООБЩЕНИЕ =====
            NewMessageBody messageBody = new NewMessageBody(
                    response.getText(),
                    null, null, null, null
            );

            // ===== КНОПКИ =====

            if (response.getButtons() != null && !response.getButtons().isEmpty()) {
                log.info("🔘 Добавляем {} кнопок", response.getButtons().size());

                List<List<ru.max.botapi.model.Button>> rows = new ArrayList<>();
                List<ru.max.botapi.model.Button> currentRow = new ArrayList<>();

                for (UniversalResponse.Button btn : response.getButtons()) {
                    log.info("🔘 Кнопка: {} | callbackData: {} | type: {}",
                            btn.getText(), btn.getCallbackData(), btn.getType());

                    ru.max.botapi.model.Button maxButton;

                    if ("request_contact".equals(btn.getType())) {
                        maxButton = new ru.max.botapi.model.RequestContactButton(btn.getText());
                    } else if ("url".equals(btn.getType()) && btn.getUrl() != null) {
                        log.info("🔗 Создаём LinkButton: {} → {}", btn.getText(), btn.getUrl());
                        maxButton = new ru.max.botapi.model.LinkButton(
                                btn.getText(),
                                btn.getUrl()
                        );
                    } else {
                        if (btn.getCallbackData() == null) {
                            log.warn("⚠️ Пропускаем кнопку без callbackData: {}", btn.getText());
                            continue;
                        }
                        maxButton = new ru.max.botapi.model.CallbackButton(
                                btn.getText(),
                                btn.getCallbackData(),
                                null
                        );
                    }

                    // ===== ЕСЛИ КНОПКА НА ВСЮ СТРОКУ =====
                    if (btn.isFullRow()) {
                        // Если в текущей строке есть кнопки — сохраняем их
                        if (!currentRow.isEmpty()) {
                            rows.add(currentRow);
                            currentRow = new ArrayList<>();
                        }
                        // Добавляем кнопку как отдельную строку
                        List<ru.max.botapi.model.Button> fullRow = new ArrayList<>();
                        fullRow.add(maxButton);
                        rows.add(fullRow);
                    } else {
                        // Обычная кнопка — добавляем в текущую строку
                        if (currentRow.size() == 2) {
                            rows.add(currentRow);
                            currentRow = new ArrayList<>();
                        }
                        currentRow.add(maxButton);
                    }
                }

                // Добавляем последнюю строку
                if (!currentRow.isEmpty()) {
                    rows.add(currentRow);
                }

                // Создаём клавиатуру
                ru.max.botapi.model.InlineKeyboardAttachment.KeyboardPayload payload =
                        new ru.max.botapi.model.InlineKeyboardAttachment.KeyboardPayload(rows);

                ru.max.botapi.model.InlineKeyboardAttachmentRequest attachmentRequest =
                        new ru.max.botapi.model.InlineKeyboardAttachmentRequest(payload);

                List<ru.max.botapi.model.AttachmentRequest> attachments = new ArrayList<>();
                attachments.add(attachmentRequest);

                messageBody = new NewMessageBody(
                        response.getText(),
                        attachments,
                        null, null, null
                );
            }

            // ===== ОТПРАВКА =====
            // ===== ОТПРАВКА =====
            try {
                api.sendMessage(messageBody)
                        .chatId(chatId)  // ← используем chatId получателя
                        .execute();
                log.info("✅ Ответ отправлен в чат {}", chatId);
            } catch (Exception e) {
                log.error("❌ Не удалось отправить сообщение в чат {}: {}", chatId, e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("404")) {
                log.warn("⚠️ Пользователь {} ещё не взаимодействовал с ботом", chatId);
            } else {
                log.error("❌ Ошибка отправки ответа: {}", errorMsg, e);
            }
        }
    }
    public void handleContactFromMax(Long userId, String contactVcf) {
        log.info("📱 ===== ОБРАБОТКА КОНТАКТА =====");
        log.info("📱 userId: " + userId);
        log.info("📱 contactVcf: " + contactVcf);

        try {
            String phone = extractPhoneFromVcf(contactVcf);
            if (phone == null) {
                log.info("❌ Не удалось извлечь телефон из контакта");
                return;
            }

            log.info("📱 Извлечен телефон: " + phone);

            String purpose = stateService.getTempPurpose(userId);
            log.info("📱 purpose: " + purpose);

            Elder tempElder = stateService.getTempElder(userId);

            stateService.clearTempPurpose(userId);

            if ("elder_phone".equals(purpose) && tempElder != null) {
                tempElder.setClientPhone(phone);
                stateService.setState(userId, DialogState.AWAITING_ELDER_REQUIREMENTS);

                UniversalResponse response = new UniversalResponse(
                        "✅ Номер телефона получен!\n\n" +
                                "📝 Теперь введите особые пожелания (например: первый этаж, диетическое питание):"
                );
                response.addButton("❌ Отменить", "cancel_action");
                sendResponse(userId, response);
            } else {
                log.info("⚠️ Нет активной сессии для пользователя " + userId);
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обработки контакта: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private String extractPhoneFromVcf(String vcf) {
        if (vcf == null || vcf.isEmpty()) return null;

        try {
            String[] lines = vcf.split("\n");
            for (String line : lines) {
                String trimmedLine = line.trim();
                if (trimmedLine.toUpperCase().startsWith("TEL")) {
                    int colonIndex = trimmedLine.indexOf(':');
                    if (colonIndex != -1) {
                        String phone = trimmedLine.substring(colonIndex + 1).trim();
                        phone = phone.replaceAll("[^0-9+]", "");
                        if (phone.length() >= 10) {
                            return phone;
                        }
                    }
                }
            }

            String phone = vcf.replaceAll("[^0-9+]", "");
            if (phone.length() >= 10) {
                return phone;
            }
            return null;

        } catch (Exception e) {
            System.err.println("❌ Ошибка извлечения телефона из VCF: " + e.getMessage());
            return null;
        }
    }
}