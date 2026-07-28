package org.grandparents.service;

import org.grandparents.dto.UniversalMessage;
import org.grandparents.dto.UniversalResponse;
import org.springframework.stereotype.Service;

@Service
public class BotService {

    public UniversalResponse handleMessage(UniversalMessage message) {
        String text = message.getText();

        if ("/start".equalsIgnoreCase(text)) {
            return new UniversalResponse("👋 Привет! Я эхо-бот для MAX. Напиши мне что-нибудь.");
        }

        return new UniversalResponse("📩 Ты написал: " + text);
    }
}