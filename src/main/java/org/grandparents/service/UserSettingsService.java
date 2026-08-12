package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.User;
import org.grandparents.statemachine.DialogState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserSettingsService {

    private static final Logger log = LoggerFactory.getLogger(UserSettingsService.class);

    private final UserService userService;
    private final UserStateService stateService;

    public UserSettingsService(UserService userService,
                               UserStateService stateService) {
        this.userService = userService;
        this.stateService = stateService;
    }

    // ============================================================
    // ===== МЕНЮ НАСТРОЕК =====
    // ============================================================

    public UniversalResponse showSettingsMenu(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        String city = user.getPreferredCity() != null ? user.getPreferredCity() : "не указан";
        String region = user.getPreferredRegion() != null ? user.getPreferredRegion() : "не указан";
        String budget = (user.getBudgetMin() != null && user.getBudgetMax() != null)
                ? user.getBudgetMin() + " - " + user.getBudgetMax() + " руб."
                : "не указан";
        String notifications = user.getNotificationsEnabled() != null && user.getNotificationsEnabled()
                ? "🔔 Включены" : "🔕 Отключены";
        String time = (user.getNotifyFrom() != null && user.getNotifyTo() != null)
                ? user.getNotifyFrom() + " - " + user.getNotifyTo()
                : "09:00 - 20:00";

        String settingsInfo = "⚙️ **Ваши настройки**\n\n" +
                "📍 **Город:** " + city + "\n" +
                "📍 **Регион:** " + region + "\n" +
                "💰 **Бюджет:** " + budget + "\n" +
                "🔔 **Уведомления:** " + notifications + "\n" +
                "🕐 **Время:** " + time + "\n\n" +
                "Выберите, что хотите изменить:";

        UniversalResponse response = new UniversalResponse(settingsInfo);

        // ===== РАЗДЕЛЯЕМ ГОРОД И РЕГИОН =====
        response.addButton("📍 Город", "settings_city");
        response.addButton("📍 Регион", "settings_region");
        response.addButton("💰 Бюджет", "settings_budget");
        response.addButton("🔔 Уведомления", "settings_notifications");
        response.addButton("🕐 Время", "settings_time");
        response.addButton("🏠 Главное меню", "main_menu");

        return response;
    }

    // ============================================================
    // ===== НАСТРОЙКА ГОРОДА =====
    // ============================================================

    public UniversalResponse handleSettingsCity(Long userId) {
        User user = getUserOrNull(userId);
        String currentCity = user != null && user.getPreferredCity() != null
                ? user.getPreferredCity() : "не указан";

        stateService.setState(userId, DialogState.AWAITING_SETTINGS_CITY);
        UniversalResponse response = new UniversalResponse(
                "📍 **Настройка города**\n\n" +
                        "Текущий город: **" + currentCity + "**\n\n" +
                        "Введите **новый город** или нажмите кнопку ниже, чтобы сбросить фильтр:"
        );
        response.addButtonFullRow("🌍 Все города", "settings_city_clear");
        response.addButton("❌ Отменить", "cancel_action");
        response.addButtonFullRow("🔙 Назад", "settings_menu");
        return response;
    }


    // ============================================================
    // ===== НАСТРОЙКА РЕГИОНА =====
    // ============================================================

    public UniversalResponse handleSettingsRegion(Long userId) {
        User user = getUserOrNull(userId);
        String currentRegion = user != null && user.getPreferredRegion() != null
                ? user.getPreferredRegion() : "не указан";

        stateService.setState(userId, DialogState.AWAITING_SETTINGS_REGION);
        UniversalResponse response = new UniversalResponse(
                "📍 **Настройка региона**\n\n" +
                        "Текущий регион: **" + currentRegion + "**\n\n" +
                        "Введите **новый регион** или нажмите кнопку ниже, чтобы сбросить фильтр:"
        );
        response.addButtonFullRow("🌍 Все регионы", "settings_region_clear");
        response.addButton("❌ Отменить", "cancel_action");
        response.addButtonFullRow("🔙 Назад", "settings_menu");
        return response;
    }

    // ============================================================
    // ===== НАСТРОЙКА БЮДЖЕТА =====
    // ============================================================

    public UniversalResponse handleSettingsBudget(Long userId) {
        User user = getUserOrNull(userId);
        String currentBudget = "не указан";
        if (user != null) {
            if (user.getBudgetMin() != null && user.getBudgetMax() != null) {
                currentBudget = user.getBudgetMin() + " - " + user.getBudgetMax() + " руб.";
            } else if (user.getBudgetMin() != null) {
                currentBudget = "от " + user.getBudgetMin() + " руб.";
            } else if (user.getBudgetMax() != null) {
                currentBudget = "до " + user.getBudgetMax() + " руб.";
            }
        }

        stateService.setState(userId, DialogState.AWAITING_SETTINGS_BUDGET_MIN);
        UniversalResponse response = new UniversalResponse(
                "💰 **Настройка бюджета**\n\n" +
                        "Текущий бюджет: **" + currentBudget + "**\n\n" +
                        "Введите **минимальный** бюджет или нажмите кнопку ниже, чтобы сбросить фильтр:\n" +
                        "Например: 30000"
        );
        response.addButtonFullRow("💰 Все бюджеты", "settings_budget_clear");
        response.addButton("❌ Отменить", "cancel_action");
        response.addButtonFullRow("🔙 Назад", "settings_menu");
        return response;
    }

    // ============================================================
    // ===== НАСТРОЙКА ВРЕМЕНИ =====
    // ============================================================

    public UniversalResponse handleSettingsTime(Long userId) {
        stateService.setState(userId, DialogState.AWAITING_SETTINGS_TIME_FROM);
        UniversalResponse response = new UniversalResponse(
                "🕐 Введите **время начала** уведомлений (в формате ЧЧ:ММ):\n\nНапример: 09:00"
        );
        response.addButton("❌ Отменить", "cancel_action");
        response.addButtonFullRow("🔙 Назад", "settings_menu");
        return response;
    }

    // ============================================================
    // ===== НАСТРОЙКА УВЕДОМЛЕНИЙ =====
    // ============================================================

    public UniversalResponse showNotificationsSettings(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        boolean enabled = user.getNotificationsEnabled() != null && user.getNotificationsEnabled();
        String status = enabled ? "🔔 **ВКЛЮЧЕНЫ** ✅" : "🔕 **ВЫКЛЮЧЕНЫ** ❌";
        String description = enabled
                ? "Вы будете получать уведомления о новых заявках."
                : "Вы не будете получать уведомления о новых заявках.";
        String buttonText = enabled ? "🔕 Выключить уведомления" : "🔔 Включить уведомления";

        UniversalResponse response = new UniversalResponse(
                "🔔 **Уведомления**\n\n" +
                        "Текущий статус: " + status + "\n\n" +
                        description + "\n\n" +
                        "Нажмите кнопку ниже, чтобы изменить:"
        );
        response.addButton(buttonText, "settings_notifications_toggle");
        response.addButton("⚙️ Настройки", "settings_menu");
        response.addButton("🏠 Главное меню", "main_menu");
        return response;
    }

    public UniversalResponse toggleNotifications(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        boolean current = user.getNotificationsEnabled() != null && user.getNotificationsEnabled();
        boolean newState = !current;
        user.setNotificationsEnabled(newState);
        userService.saveUser(user);

        return showNotificationsSettings(userId);
    }

    // ============================================================
    // ===== ОБРАБОТКА ВВОДА НАСТРОЕК =====
    // ============================================================

    public UniversalResponse handleSettingsInput(Long userId, String text, DialogState state) {
        User user = getUserOrNull(userId);

        switch (state) {
            case AWAITING_SETTINGS_CITY -> {
                if (user != null) {
                    user.setPreferredCity(text.trim());
                    userService.saveUser(user);
                    UniversalResponse response = new UniversalResponse("✅ Город сохранён: " + text.trim());
                    response.addButton("⚙️ Настройки", "settings_menu");
                    response.addButton("🏠 Главное меню", "main_menu");
                    return response;
                }
                return responseWithMainMenu("❌ Пользователь не найден.");
            }

            case AWAITING_SETTINGS_REGION -> {
                if (user != null) {
                    user.setPreferredRegion(text.trim());
                    userService.saveUser(user);
                    UniversalResponse response = new UniversalResponse("✅ Регион сохранён: " + text.trim());
                    response.addButton("⚙️ Настройки", "settings_menu");
                    response.addButton("🏠 Главное меню", "main_menu");
                    return response;
                }
                return responseWithMainMenu("❌ Пользователь не найден.");
            }

            case AWAITING_SETTINGS_BUDGET_MIN -> {
                try {
                    double min = Double.parseDouble(text);
                    if (min < 0) {
                        UniversalResponse response = new UniversalResponse("❌ Бюджет не может быть отрицательным. Введите число:");
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    stateService.setTempBudgetMin(userId, min);
                    stateService.setState(userId, DialogState.AWAITING_SETTINGS_BUDGET_MAX);
                    UniversalResponse response = new UniversalResponse(
                            "💰 Введите **максимальный** бюджет (в рублях):\n\nНапример: 100000"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                } catch (NumberFormatException e) {
                    UniversalResponse response = new UniversalResponse("❌ Введите число (например: 30000):");
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }

            case AWAITING_SETTINGS_BUDGET_MAX -> {
                try {
                    double max = Double.parseDouble(text);
                    Double min = stateService.getTempBudgetMin(userId);
                    if (min != null && max < min) {
                        UniversalResponse response = new UniversalResponse(
                                "❌ Максимальный бюджет (" + max + ") должен быть больше минимального (" + min + ")."
                        );
                        response.addButton("❌ Отменить", "cancel_action");
                        return response;
                    }
                    if (user != null) {
                        user.setBudgetMin(min);
                        user.setBudgetMax(max);
                        userService.saveUser(user);
                        stateService.clearState(userId);
                        UniversalResponse response = new UniversalResponse(
                                "✅ Бюджет сохранён: " + min + " - " + max + " руб."
                        );
                        response.addButton("⚙️ Настройки", "settings_menu");
                        response.addButton("🏠 Главное меню", "main_menu");
                        return response;
                    }
                    return responseWithMainMenu("❌ Пользователь не найден.");
                } catch (NumberFormatException e) {
                    UniversalResponse response = new UniversalResponse("❌ Введите число (например: 100000):");
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
            }

            case AWAITING_SETTINGS_TIME_FROM -> {
                if (!text.matches("^([0-1][0-9]|2[0-3]):[0-5][0-9]$")) {
                    UniversalResponse response = new UniversalResponse(
                            "❌ Неверный формат. Введите время в формате ЧЧ:ММ (например: 09:00):"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
                stateService.setTempTimeFrom(userId, text);
                stateService.setState(userId, DialogState.AWAITING_SETTINGS_TIME_TO);
                UniversalResponse response = new UniversalResponse(
                        "🕐 Введите **время окончания** уведомлений (в формате ЧЧ:ММ):\n\nНапример: 20:00"
                );
                response.addButton("❌ Отменить", "cancel_action");
                return response;
            }

            case AWAITING_SETTINGS_TIME_TO -> {
                if (!text.matches("^([0-1][0-9]|2[0-3]):[0-5][0-9]$")) {
                    UniversalResponse response = new UniversalResponse(
                            "❌ Неверный формат. Введите время в формате ЧЧ:ММ (например: 20:00):"
                    );
                    response.addButton("❌ Отменить", "cancel_action");
                    return response;
                }
                String timeFrom = stateService.getTempTimeFrom(userId);
                if (user != null && timeFrom != null) {
                    user.setNotifyFrom(timeFrom);
                    user.setNotifyTo(text);
                    userService.saveUser(user);
                    stateService.clearState(userId);
                    UniversalResponse response = new UniversalResponse(
                            "✅ Время уведомлений сохранено: " + timeFrom + " - " + text
                    );
                    response.addButton("⚙️ Настройки", "settings_menu");
                    response.addButton("🏠 Главное меню", "main_menu");
                    return response;
                }
                return responseWithMainMenu("❌ Пользователь не найден.");
            }

            default -> {
                return responseWithMainMenu("❌ Неизвестный шаг настройки.");
            }
        }
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private User getUserOrNull(Long userId) {
        return userService.findByTelegramId(userId).orElse(null);
    }

    private UniversalResponse responseWithMainMenu(String text) {
        UniversalResponse response = new UniversalResponse(text);
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
}