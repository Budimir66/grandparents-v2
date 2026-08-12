package org.grandparents.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ElderStatus {
    @JsonProperty("Новая")
    NEW("🟢 Новая"),

    @JsonProperty("Предложена")
    OFFERED("🟡 Предложена партнёрам"),

    @JsonProperty("В работе")
    IN_PROGRESS("🟠 В работе у оператора"),

    @JsonProperty("Принята")
    ACCEPTED("🔵 Принята оператором"),

    @JsonProperty("Завершена")
    COMPLETED("✅ Завершена"),

    @JsonProperty("Редактируется")
    EDITED("✏️ Редактируется клиентом"),

    @JsonProperty("Истекла")
    EXPIRED("⏰ Истекла"),

    @JsonProperty("Удалена")
    DELETED("❌ Удалена"),

    @JsonProperty("Ожидает подтверждения")
    AWAITING_CONFIRMATION("⏳ Ожидает подтверждения"),

    @JsonProperty("На модерации")
    PENDING("⏳ На модерации");  // ← НОВЫЙ СТАТУС!

    private final String displayName;

    ElderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }


}