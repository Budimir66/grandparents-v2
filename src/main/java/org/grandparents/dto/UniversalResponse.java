package org.grandparents.dto;

import java.util.ArrayList;
import java.util.List;

public class UniversalResponse {
    private String text;
    private List<Button> buttons = new ArrayList<>();
    private String contactPurpose;
    private String imageUrl;

    public UniversalResponse() {}

    public UniversalResponse(String text) {
        this.text = text;
    }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<Button> getButtons() { return buttons; }
    public void setButtons(List<Button> buttons) { this.buttons = buttons; }

    public String getContactPurpose() { return contactPurpose; }
    public void setContactPurpose(String contactPurpose) { this.contactPurpose = contactPurpose; }

    // ===== МЕТОДЫ ДЛЯ КНОПОК =====

    public void addButton(String text, String callbackData) {
        Button button = new Button(text, callbackData);
        button.setFullRow(false);
        this.buttons.add(button);
    }

    public void addButtonFullRow(String text, String callbackData) {
        Button button = new Button(text, callbackData);
        button.setFullRow(true);
        this.buttons.add(button);
    }

    public void addButtonsInRow(String... buttonsData) {
        if (buttonsData == null || buttonsData.length % 2 != 0) {
            throw new IllegalArgumentException("Количество аргументов должно быть четным");
        }

        for (int i = 0; i < buttonsData.length && i < 4; i += 2) {
            String text = buttonsData[i];
            String callbackData = buttonsData[i + 1];
            Button button = new Button(text, callbackData);
            button.setFullRow(false);
            this.buttons.add(button);
        }
    }

    public void addContactRequestButton(String text) {
        Button button = new Button();
        button.setText(text);
        button.setType("request_contact");
        button.setFullRow(true);
        this.buttons.add(button);
    }

    public void addMainMenuButton() {
        addButtonFullRow("🏠 Главное меню", "main_menu");
    }

    // ===== ВНУТРЕННИЙ КЛАСС КНОПКИ =====
    public static class Button {
        private String text;
        private String callbackData;
        private String type;
        private boolean fullRow;
        private String url;  // для URL-кнопок

        public Button() {
            this.fullRow = false;
        }

        public Button(String text, String callbackData) {
            this.text = text;
            this.callbackData = callbackData;
            this.type = "callback";
            this.fullRow = false;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getCallbackData() { return callbackData; }
        public void setCallbackData(String callbackData) { this.callbackData = callbackData; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isFullRow() { return fullRow; }
        public void setFullRow(boolean fullRow) { this.fullRow = fullRow; }
    }
    /**
     * Добавляет кнопку-ссылку (URL)
     * @param text Текст кнопки
     * @param url Ссылка для перехода
     */
    public void addUrlButton(String text, String url) {
        Button button = new Button();
        button.setText(text);
        button.setType("url");
        button.setUrl(url);
        this.buttons.add(button);
    }
    /**
     * Добавляет кнопку-ссылку (URL) на всю ширину строки
     * @param text Текст кнопки
     * @param url Ссылка для перехода
     */
    public void addUrlButtonFullRow(String text, String url) {
        Button button = new Button();
        button.setText(text);
        button.setType("url");
        button.setUrl(url);
        button.setFullRow(true);
        this.buttons.add(button);
    }

}