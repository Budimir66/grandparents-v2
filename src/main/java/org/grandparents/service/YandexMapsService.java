package org.grandparents.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class YandexMapsService {

    private final String apiKey;
    private final String staticKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public YandexMapsService(
            @Value("${yandex.maps.api.key}") String apiKey,
            @Value("${yandex.maps.static.key}") String staticKey) {
        this.apiKey = apiKey;
        this.staticKey = staticKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Преобразует адрес в координаты (геокодирование)
     */
    public double[] geocodeAddress(String address) {
        try {
            String url = "https://geocode-maps.yandex.ru/1.x/"
                    + "?apikey=" + apiKey
                    + "&geocode=" + java.net.URLEncoder.encode(address, "UTF-8")
                    + "&format=json";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("❌ Ошибка геокодирования: " + response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode pos = root
                    .path("response")
                    .path("GeoObjectCollection")
                    .path("featureMember")
                    .path(0)
                    .path("GeoObject")
                    .path("Point")
                    .path("pos");

            if (pos.isMissingNode()) {
                System.err.println("❌ Адрес не найден: " + address);
                return null;
            }

            String[] coords = pos.asText().split(" ");
            double lon = Double.parseDouble(coords[0]);
            double lat = Double.parseDouble(coords[1]);

            System.out.println("✅ Геокодирование успешно: " + address + " → " + lat + ", " + lon);
            return new double[]{lat, lon};

        } catch (Exception e) {
            System.err.println("❌ Ошибка геокодирования: " + e.getMessage());
            return null;
        }
    }

    /**
     * Генерирует ссылку на статическую карту
     */
    public String getStaticMapUrl(double lat, double lon, String label) {
        // Формируем метку: название пансионата
        String labelText = label != null ? "label:" + label : "";

        return "https://static-maps.yandex.ru/v1?"
                + "ll=" + lon + "," + lat
                + "&z=15"
                + "&size=600,300"
                + "&pt=" + lon + "," + lat + ",pm2rdm"
                + "&apikey=" + staticKey;
    }

    /**
     * Генерирует ссылку на Яндекс.Карты для просмотра
     */
    public String getMapLink(double lat, double lon, String label) {
        String labelText = label != null ? "&text=" + java.net.URLEncoder.encode(label) : "";
        return "https://yandex.ru/maps/?pt=" + lon + "," + lat + "&z=17" + labelText;
    }
}