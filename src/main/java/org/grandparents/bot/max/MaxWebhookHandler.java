package org.grandparents.bot.max;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;

import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.grandparents.service.BotService;
import org.springframework.beans.factory.annotation.Value;
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

import java.time.Duration;

@RestController
@RequestMapping("/webhook/max")
@Component
public class MaxWebhookHandler {

    private final BotService botService;
    private final String maxToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MaxBotAPI api;

    public MaxWebhookHandler(BotService botService,
                             @Value("${max.bot.webhook.token}") String maxToken) {
        this.botService = botService;
        this.maxToken = maxToken;
        System.out.println("✅ MAX Webhook Handler создан!");
    }

    @PostConstruct
    public void init() throws Exception {
        // ===== СОЗДАЁМ КЛИЕНТ =====
        MaxClientConfig config = MaxClientConfig.builder()
                .baseUrl("https://platform-api2.max.ru")
                .connectTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(60))  // ← ИСПРАВЛЕНО
                .build();

        JdkHttpMaxTransportClient transport = new JdkHttpMaxTransportClient(maxToken, config);
        JacksonMaxSerializer serializer = new JacksonMaxSerializer();
        MaxClient client = new MaxClient(transport, serializer, config);
        this.api = new MaxBotAPI(client);

        System.out.println("✅ MAX Bot API создан!");
    }

    @PostMapping
    public String handleWebhook(@RequestBody String body) {
        System.out.println("📩 MAX Webhook: " + body);

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode message = root.get("message");
            if (message == null) {
                return "OK";
            }

            JsonNode recipient = message.get("recipient");
            JsonNode sender = message.get("sender");
            JsonNode bodyNode = message.get("body");

            if (recipient == null || sender == null || bodyNode == null) {
                System.out.println("⚠️ Неполные данные в Webhook");
                return "OK";
            }

            Long chatId = recipient.get("chat_id") != null ? recipient.get("chat_id").asLong() : null;
            String text = bodyNode.get("text") != null ? bodyNode.get("text").asText() : "";

            if (chatId == null || text.isEmpty()) {
                System.out.println("⚠️ Нет текста или chat_id");
                return "OK";
            }

            System.out.println("📩 Сообщение от " + sender.get("first_name").asText() + ": " + text);

            UniversalMessage msg = new UniversalMessage();
            msg.setPlatform("max");
            msg.setChatId(String.valueOf(chatId));
            msg.setText(text);

            UniversalResponse response = botService.handleMessage(msg);

            if (response != null && response.getText() != null) {
                NewMessageBody messageBody = new NewMessageBody(
                        response.getText(),
                        null, null, null, null
                );
                api.sendMessage(messageBody)
                        .chatId(chatId)
                        .execute();
                System.out.println("✅ Ответ отправлен в MAX!");
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка обработки MAX Webhook: " + e.getMessage());
            e.printStackTrace();
        }

        return "OK";
    }
}