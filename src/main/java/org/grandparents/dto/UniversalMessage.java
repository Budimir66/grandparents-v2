package org.grandparents.dto;

public class UniversalMessage {
    private String platform;
    private String userId;
    private String chatId;
    private String text;

    public UniversalMessage() {}

    public UniversalMessage(String platform, String userId, String chatId, String text) {
        this.platform = platform;
        this.userId = userId;
        this.chatId = chatId;
        this.text = text;
    }

    // Геттеры и сеттеры
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    @Override
    public String toString() {
        return "UniversalMessage{" +
                "platform='" + platform + '\'' +
                ", userId='" + userId + '\'' +
                ", chatId='" + chatId + '\'' +
                ", text='" + text + '\'' +
                '}';
    }
}