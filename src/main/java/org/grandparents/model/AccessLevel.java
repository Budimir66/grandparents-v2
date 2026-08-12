package org.grandparents.model;

public enum AccessLevel {

    GUEST(0),
    CLIENT(1),
    MANAGER(2),      // ← НОВАЯ РОЛЬ: директор пансионата (может регистрировать операторов)
    OPERATOR(3),
    ADMIN(4);

    private final int level;

    AccessLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean hasAccess(AccessLevel required) {
        return this.level >= required.getLevel();
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    public boolean isOperatorOrHigher() {
        return this.level >= OPERATOR.getLevel();
    }

    public boolean isManagerOrHigher() {
        return this.level >= MANAGER.getLevel();
    }

    public boolean isClientOrHigher() {
        return this.level >= CLIENT.getLevel();
    }

    @Override
    public String toString() {
        return switch (this) {
            case GUEST -> "👤 Гость";
            case CLIENT -> "👤 Клиент";
            case MANAGER -> "🏢 Директор";
            case OPERATOR -> "🔑 Оператор";
            case ADMIN -> "👑 Администратор";
        };
    }
}