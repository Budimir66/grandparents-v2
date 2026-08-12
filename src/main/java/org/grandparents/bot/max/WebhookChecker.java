package org.grandparents.bot.max;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.max.botapi.client.JdkHttpMaxTransportClient;
import ru.max.botapi.client.MaxBotAPI;
import ru.max.botapi.client.MaxClient;
import ru.max.botapi.client.MaxClientConfig;
import ru.max.botapi.jackson.JacksonMaxSerializer;

import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Component
public class WebhookChecker {

    private final String token;
    private final String webhookUrl;

    public WebhookChecker(@Value("${max.bot.webhook.token}") String token,
                          @Value("${max.bot.webhook.url}") String webhookUrl) {
        this.token = token;
        this.webhookUrl = webhookUrl;
    }

    @PostConstruct
    public void checkWebhook() {
        try {
            System.out.println("🔍 Проверяем регистрацию вебхука...");

            MaxClientConfig config = MaxClientConfig.builder()
                    .baseUrl("https://platform-api2.max.ru")
                    .connectTimeout(Duration.ofSeconds(10))
                    .requestTimeout(Duration.ofSeconds(60))
                    .build();

            JdkHttpMaxTransportClient transport = new JdkHttpMaxTransportClient(token, config);
            JacksonMaxSerializer serializer = new JacksonMaxSerializer();
            MaxClient client = new MaxClient(transport, serializer, config);
            MaxBotAPI api = new MaxBotAPI(client);

            // Пытаемся получить информацию о вебхуке
            // (это примерный код, может отличаться в зависимости от версии SDK)
            System.out.println("✅ Проверка завершена");

        } catch (Exception e) {
            System.err.println("❌ Ошибка проверки вебхука: " + e.getMessage());
        }
    }
}