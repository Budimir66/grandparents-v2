package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.AccessLevel;
import org.grandparents.model.BonusSetting;
import org.grandparents.model.CareHome;
import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.grandparents.model.User;
import org.grandparents.statemachine.DialogState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserService userService;
    private final ElderService elderService;
    private final CareHomeService careHomeService;
    private final UserStateService stateService;
    private final BonusSettingService bonusSettingService;
    private final MessageSender messageSender;

    public AdminService(UserService userService,
                        ElderService elderService,
                        CareHomeService careHomeService,
                        UserStateService stateService,
                        BonusSettingService bonusSettingService,
                        MessageSender messageSender) {
        this.userService = userService;
        this.elderService = elderService;
        this.careHomeService = careHomeService;
        this.stateService = stateService;
        this.bonusSettingService = bonusSettingService;
        this.messageSender = messageSender;
    }

    // ============================================================
    // ===== АДМИН-ПАНЕЛЬ =====
    // ============================================================

    public UniversalResponse showAdminMenu(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null || user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        UniversalResponse response = new UniversalResponse(
                "⚙️ **Админ-панель**\n\nУправление системой:\n━━━━━━━━━━━━━━━━━━━━━━━"
        );

        response.addButtonFullRow("👥 Управление операторами", "admin_operators");
        response.addButtonFullRow("🏢 Управление пансионатами", "admin_carehomes_menu");
        response.addButtonFullRow("📋 Модерация заявок", "admin_elder_moderation");
        response.addButtonFullRow("📊 Общая статистика", "admin_stats");
        response.addButtonFullRow("💰 Настройка бонусов", "admin_bonus_settings");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }

    // ============================================================
    // ===== УПРАВЛЕНИЕ ОПЕРАТОРАМИ =====
    // ============================================================

    public UniversalResponse showOperatorsList(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        List<User> allOperators = userService.findAllByAccessLevel(AccessLevel.OPERATOR);

        if (allOperators.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "👥 **Операторы**\n\nЗарегистрированных операторов пока нет."
            );
            response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== ПАГИНАЦИЯ =====
        int page = stateService.getCurrentPage(userId);
        int pageSize = 10;
        int totalPages = (int) Math.ceil((double) allOperators.size() / pageSize);

        if (page >= totalPages) {
            page = 0;
            stateService.setCurrentPage(userId, 0);
        }

        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, allOperators.size());
        List<User> operators = allOperators.subList(fromIndex, toIndex);

        // ===== ФОРМИРУЕМ ОТВЕТ =====
        StringBuilder sb = new StringBuilder();
        sb.append("👥 **Операторы (страница ").append(page + 1).append(" из ").append(totalPages).append("):**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ ДЛЯ КАЖДОГО ОПЕРАТОРА =====
        for (User operator : operators) {
            String careHomeName = getCareHomeName(operator.getCareHomeId());
            String statusIcon = operator.getIsActive() != null && operator.getIsActive() ? "🟢" : "🔴";
            String buttonText = careHomeName + " — " + operator.getFirstName();
            response.addButtonFullRow(buttonText, "admin_view_operator_" + operator.getId());
        }

        // ===== КНОПКИ ПАГИНАЦИИ =====
        if (page > 0) {
            response.addButtonFullRow("⬅️ Предыдущая", "admin_operators_prev");
        }
        if (page < totalPages - 1) {
            response.addButtonFullRow("➡️ Следующая", "admin_operators_next");
        }

        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== УПРАВЛЕНИЕ ПАНСИОНАТАМИ (АДМИН) =====
    // ============================================================

    public UniversalResponse showAdminCarehomesMenu(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        UniversalResponse response = new UniversalResponse(
                "🏢 **Управление пансионатами**\n\nВыберите действие:"
        );

        response.addButtonFullRow("📋 Список пансионатов", "admin_carehomes_list");
        response.addButtonFullRow("➕ Добавить пансионат", "admin_carehomes_add");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }

    public UniversalResponse showAdminCarehomesList(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        List<CareHome> careHomes = careHomeService.findAll();

        if (careHomes.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "🏢 **Пансионаты**\n\nЗарегистрированных пансионатов пока нет."
            );
            response.addButtonFullRow("➕ Добавить пансионат", "admin_carehomes_add");
            response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        UniversalResponse response = new UniversalResponse(
                "🏢 **Пансионаты**\n\nВыберите пансионат для управления:"
        );

        for (CareHome careHome : careHomes) {
            String activeStatus = careHome.getIsActive() != null && careHome.getIsActive() ? "🟢" : "🔴";

            // ===== ПРОВЕРЯЕМ, ЧТО priceFrom НЕ РАВЕН 0 (значение по умолчанию) =====
            String priceText = (careHome.getPriceFrom() > 0) ? "от " + careHome.getPriceFrom() + " руб." : "";
            String buttonText = activeStatus + " " + careHome.getName() + (priceText.isEmpty() ? "" : " (" + priceText + ")");
            response.addButtonFullRow(buttonText, "admin_carehome_view_" + careHome.getId());
        }

        response.addButtonFullRow("➕ Добавить пансионат", "admin_carehomes_add");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    public UniversalResponse showAdminCarehomeCard(Long userId, CareHome careHome) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        String statusIcon = getCareHomeStatusIcon(careHome.getStatus());
        String activeStatus = careHome.getIsActive() != null && careHome.getIsActive() ? "🟢 Активен" : "🔴 Неактивен";
        String subscriptionStatus = careHome.getIsSubscribed() != null && careHome.getIsSubscribed()
                ? "✅ Активна" : "❌ Неактивна";

        String card = "🏢 **" + careHome.getName() + "**\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📌 **Статус:** " + statusIcon + " " + careHome.getStatus() + "\n" +
                "🔘 **Активен:** " + activeStatus + "\n" +
                "📅 **Подписка:** " + subscriptionStatus + "\n";

        if (careHome.getSubscriptionEnd() != null) {
            card += "📆 **До:** " + careHome.getSubscriptionEnd() + "\n";
        }
        if (careHome.getWebsite() != null && !careHome.getWebsite().isEmpty() && !careHome.getWebsite().equals("-")) {
            card += "🌐 **Сайт:** " + careHome.getWebsite() + "\n";
        }
        card += "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📍 **Адрес:** " + careHome.getAddress() + "\n" +
                "📞 **Телефон:** " + careHome.getPhone() + "\n" +
                "💰 **Цена от:** " + careHome.getPriceFrom() + " руб.\n" +
                "📝 **Описание:** " + careHome.getDescription() + "\n" +
                "🏥 **Специализация:** " + careHome.getSpecialization() + "\n";

        if (careHome.getProposedBy() != null) {
            User proposer = getUserOrNull(careHome.getProposedBy());
            card += "\n👤 **Предложил:** " + (proposer != null ? proposer.getFirstName() : "Неизвестно");
        }

        UniversalResponse response = new UniversalResponse(card);

        if (careHome.getLatitude() != null && careHome.getLongitude() != null) {
            String mapLink = "https://yandex.ru/maps/?pt=" +
                    careHome.getLongitude() + "," + careHome.getLatitude() + "&z=17";
            response.addUrlButtonFullRow("🗺️ Показать на Яндекс.Карте", mapLink);
        }

        if ("PENDING".equals(careHome.getStatus())) {
            response.addButtonFullRow("✅ Одобрить пансионат", "admin_approve_carehome_" + careHome.getId());
        }
        response.addButtonFullRow("✏️ Редактировать", "admin_carehome_edit_" + careHome.getId());

        if (careHome.getIsActive() != null && careHome.getIsActive()) {
            response.addButtonFullRow("🔒 Заблокировать", "admin_carehome_block_" + careHome.getId());
        } else {
            response.addButtonFullRow("🔓 Разблокировать", "admin_carehome_unblock_" + careHome.getId());
        }

        response.addButtonFullRow("🗑️ Удалить", "admin_carehome_delete_" + careHome.getId());
        response.addButtonFullRow("📋 Назад к списку", "admin_carehomes_list");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }

    // ============================================================
    // ===== СТАТИСТИКА =====
    // ============================================================

    public UniversalResponse showAdminStats(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        long totalUsers = userService.countAll();
        long totalOperators = userService.countByAccessLevel(AccessLevel.OPERATOR);
        long totalElders = elderService.countAll();
        long activeElders = elderService.countByStatusIn(List.of(ElderStatus.NEW, ElderStatus.OFFERED, ElderStatus.IN_PROGRESS));
        long completedElders = elderService.countByStatus(ElderStatus.COMPLETED);

        String stats = "📊 **Общая статистика**\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👥 **Пользователи:**\n" +
                "  • Всего: " + totalUsers + "\n" +
                "  • Операторы: " + totalOperators + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📋 **Заявки:**\n" +
                "  • Всего: " + totalElders + "\n" +
                "  • Активные: " + activeElders + "\n" +
                "  • Завершённые: " + completedElders + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━";

        UniversalResponse response = new UniversalResponse(stats);
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== НАСТРОЙКА БОНУСОВ =====
    // ============================================================

    public UniversalResponse showBonusSettings(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        List<BonusSetting> settings = bonusSettingService.findAll();

        if (settings.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "💰 **Настройка бонусов**\n\nНастроек бонусов пока нет."
            );
            response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        StringBuilder sb = new StringBuilder("💰 **Настройка бонусов**\n\n");
        sb.append("Текущие значения:\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");

        for (BonusSetting setting : settings) {
            String sign = setting.getValue() > 0 ? "+" : "";
            sb.append("📌 ").append(setting.getActionName())
                    .append(":  ").append(sign).append(setting.getValue())
                    .append(" балл");
            if (Math.abs(setting.getValue()) != 1) sb.append("а");
            sb.append("\n");
        }

        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        sb.append("Выберите, что изменить:");

        UniversalResponse response = new UniversalResponse(sb.toString());

        for (BonusSetting setting : settings) {
            response.addButtonFullRow("📌 " + setting.getActionName(), "admin_bonus_edit_" + setting.getActionKey());
        }

        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }

    // ============================================================
    // ===== МОДЕРАЦИЯ ЗАЯВОК =====
    // ============================================================

    public UniversalResponse showElderModerationList(Long userId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        List<Elder> pendingElders = elderService.findByStatus(ElderStatus.PENDING);

        if (pendingElders.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "📋 **Модерация заявок**\n\nЗаявок, ожидающих модерации, нет."
            );
            response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        StringBuilder sb = new StringBuilder("📋 **Модерация заявок**\n\n");
        sb.append("Заявки, ожидающие проверки:\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");

        for (Elder elder : pendingElders) {
            User client = getUserOrNull(elder.getClientTelegramId());
            String clientName = client != null ? client.getFirstName() : "Неизвестный";

            sb.append("⏳ #").append(elder.getId())
                    .append(" ").append(elder.getFullName())
                    .append(" | ").append(elder.getAge()).append(" лет")
                    .append(" | ").append(elder.getBudget()).append(" руб.")
                    .append("\n👤 Клиент: ").append(clientName)
                    .append("\n📱 ").append(elder.getClientPhone())
                    .append("\n━━━━━━━━━━━━━━━━━━━━━━━\n");
        }

        UniversalResponse response = new UniversalResponse(sb.toString());

        for (Elder elder : pendingElders) {
            response.addButtonFullRow(
                    "📋 Заявка #" + elder.getId() + " — " + elder.getFullName(),
                    "admin_elder_view_" + elder.getId()
            );
        }

        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    public UniversalResponse showAdminElderCard(Long userId, Elder elder) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User client = getUserOrNull(elder.getClientTelegramId());
        String clientName = client != null ? client.getFirstName() : "Неизвестный";

        String card = "📋 **Заявка #" + elder.getId() + "** (⏳ на модерации)\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 **Клиент:** " + clientName + "\n" +
                "📱 **Телефон:** " + elder.getClientPhone() + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "🎂 **Возраст:** " + elder.getAge() + " лет\n" +
                "💊 **Здоровье:** " + elder.getHealthCondition() + "\n" +
                "💰 **Бюджет:** " + elder.getBudget() + " руб.\n" +
                "📍 **Локация:** " + elder.getPreferredLocation() + "\n" +
                "📝 **Пожелания:** " + elder.getRequirements() + "\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📌 **Статус:** ⏳ На модерации";

        UniversalResponse response = new UniversalResponse(card);
        response.addButtonFullRow("✅ Одобрить заявку", "approve_elder_" + elder.getId());
        response.addButtonFullRow("❌ Отклонить заявку", "reject_elder_" + elder.getId());
        response.addButtonFullRow("📋 Назад к списку", "admin_elder_moderation");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private User getUserOrNull(Long userId) {
        return userService.findByTelegramId(userId).orElse(null);
    }

    private boolean isAdmin(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.ADMIN;
    }

    private String getCareHomeName(Long careHomeId) {
        if (careHomeId == null) return "не указан";
        CareHome careHome = careHomeService.findById(careHomeId);
        return careHome != null ? careHome.getName() : "не указан";
    }

    private String getCareHomeStatusIcon(String status) {
        if (status == null) return "❓";
        return switch (status) {
            case "PENDING" -> "⏳";
            case "APPROVED" -> "✅";
            case "REJECTED" -> "❌";
            case "INACTIVE" -> "🔴";
            default -> "❓";
        };
    }

    private UniversalResponse responseWithMainMenu(String text) {
        UniversalResponse response = new UniversalResponse(text);
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Одобрение пансионата администратором
     */
    public UniversalResponse handleApproveCarehome(Long userId, Long careHomeId) {
        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_menu");
        }

        User admin = userService.findByTelegramId(userId).orElse(null);
        if (admin == null || admin.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        // Одобряем пансионат
        careHome.setStatus("APPROVED");
        careHome.setActive(true);
        careHome.setModeratedBy(userId);
        careHome.setModeratedAt(LocalDateTime.now());
        careHome.setSubscribed(true);
        careHome.setSubscriptionStart(LocalDateTime.now());
        careHome.setSubscriptionEnd(LocalDateTime.now().plusDays(30));
        careHomeService.save(careHome);

        // ============================================================
        // ===== УВЕДОМЛЯЕМ АВТОРА (ДИРЕКТОРА) =====
        // ============================================================
        Long authorId = careHome.getProposedBy();
        if (authorId != null) {
            User author = userService.findById(authorId);  // ← authorId — это id пользователя
            if (author != null) {
                // Обновляем статус автора
                if (author.getAccessLevel() == AccessLevel.GUEST) {
                    author.setAccessLevel(AccessLevel.MANAGER);
                }
                author.setCareHomeId(careHome.getId());
                userService.saveUser(author);

                log.info("👤 Пользователь {} может управлять пансионатом {}",
                        author.getTelegramId(), careHome.getName());

                // Отправляем уведомление
                sendNotification(author.getTelegramId(),
                        "🎉 **Поздравляем! Ваш пансионат одобрен!**\n\n" +
                                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                                "📍 **Адрес:** " + careHome.getAddress() + "\n\n" +
                                "✅ Теперь вы можете управлять пансионатом!\n" +
                                "📌 Вам доступна панель управления:\n" +
                                "• Регистрация операторов\n" +
                                "• Управление пансионатом\n" +
                                "• Просмотр статистики\n\n" +
                                "🔑 Чтобы зарегистрировать оператора,\n" +
                                "перейдите в раздел 'Мои пансионаты'.\n\n" +
                                "🚀 Удачи!",
                        "🏢 Мои пансионаты", "my_carehomes"
                );
            }
        }

        // Ответ администратору
        UniversalResponse response = new UniversalResponse(
                "✅ Пансионат **" + careHome.getName() + "** одобрен!\n\n" +
                        "📅 Бесплатный период: 30 дней\n" +
                        "📆 До: " + careHome.getSubscriptionEnd()
        );
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Отклонение пансионата администратором
     */
    public UniversalResponse handleRejectCarehome(Long userId, Long careHomeId) {
        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "admin_menu");
        }

        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        // Сохраняем ID пансионата в состояние для ввода комментария
        stateService.setEditingCareHomeId(userId, careHomeId);
        stateService.setState(userId, DialogState.AWAITING_REJECT_COMMENT);

        UniversalResponse response = new UniversalResponse(
                "❌ **Отклонение пансионата**\n\n" +
                        "Пансионат: **" + careHome.getName() + "**\n\n" +
                        "Введите причину отказа (комментарий для оператора):"
        );
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }
    /**
     * Отправляет уведомление пользователю
     */
    private void sendNotification(Long userId, String text, String... buttons) {
        try {
            User user = getUserOrNull(userId);
            if (user == null) {
                log.warn("⚠️ Пользователь {} не найден для уведомления", userId);
                return;
            }

            UniversalResponse response = new UniversalResponse(text);
            for (int i = 0; i < buttons.length; i += 2) {
                if (i + 1 < buttons.length) {
                    response.addButtonFullRow(buttons[i], buttons[i + 1]);
                }
            }
            response.addButtonFullRow("🏠 Главное меню", "main_menu");

            Long chatId = user.getChatId() != null ? user.getChatId() : userId;
            messageSender.sendMessage(chatId, response);
            log.info("📨 Уведомление отправлено пользователю {}", userId);
        } catch (Exception e) {
            log.error("❌ Ошибка отправки уведомления пользователю {}: {}", userId, e.getMessage(), e);
        }
    }
    /**
     * Создаёт ответ с кнопками "Назад" и "Главное меню"
     */
    private UniversalResponse responseWithBackAndMainMenu(String text, String backCallback) {
        UniversalResponse response = new UniversalResponse(text);
        if (backCallback != null) {
            response.addButtonFullRow("🔙 Назад", backCallback);
        }
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Одобрить изменения пансионата
     */
    public UniversalResponse approveCarehomeEdit(Long userId, Long careHomeId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithMainMenu("❌ Пансионат не найден.");
        }

        // Изменения уже сохранены, просто подтверждаем
        careHome.setStatus("APPROVED");
        careHomeService.save(careHome);

        // Уведомляем MANAGER
        if (careHome.getProposedBy() != null) {
            sendNotification(careHome.getProposedBy(),
                    "✅ **Изменения пансионата одобрены!**\n\n" +
                            "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                            "📌 Все изменения подтверждены администратором.",
                    "🏢 Мои пансионаты", "my_carehomes"
            );
        }

        UniversalResponse response = new UniversalResponse(
                "✅ Изменения пансионата **" + careHome.getName() + "** одобрены!"
        );
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    /**
     * Отклонить изменения пансионата
     */
    public UniversalResponse rejectCarehomeEdit(Long userId, Long careHomeId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithMainMenu("❌ Пансионат не найден.");
        }

        // Возвращаем старый статус
        careHome.setStatus("APPROVED");
        careHomeService.save(careHome);

        // Уведомляем MANAGER
        if (careHome.getProposedBy() != null) {
            sendNotification(careHome.getProposedBy(),
                    "❌ **Изменения пансионата отклонены!**\n\n" +
                            "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                            "📌 Администратор отклонил ваши изменения.",
                    "🏢 Мои пансионаты", "my_carehomes"
            );
        }

        UniversalResponse response = new UniversalResponse(
                "❌ Изменения пансионата **" + careHome.getName() + "** отклонены!"
        );
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает карточку оператора для администратора
     */
    public UniversalResponse showOperatorCardForAdmin(Long userId, Long operatorId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "admin_operators");
        }

        String careHomeName = getCareHomeName(operator.getCareHomeId());
        String status = operator.getIsActive() != null && operator.getIsActive() ? "🟢 Активен" : "🔴 Заблокирован";

        // ===== СТАТИСТИКА =====
        int completed = operator.getTotalCompleted() != null ? operator.getTotalCompleted() : 0;
        int taken = operator.getTotalTaken() != null ? operator.getTotalTaken() : 0;
        int bonus = operator.getBonusPoints();
        double revenue = operator.getTotalRevenue() != null ? operator.getTotalRevenue() : 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("👤 **Карточка оператора**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("🆔 ID: ").append(operator.getId()).append("\n");
        sb.append("👤 Имя: ").append(operator.getFirstName()).append("\n");
        sb.append("📱 Телефон: ").append(operator.getPhone() != null ? operator.getPhone() : "не указан").append("\n");
        sb.append("📧 Email: ").append(operator.getEmail() != null ? operator.getEmail() : "не указан").append("\n");
        sb.append("🏢 Пансионат: ").append(careHomeName).append("\n");
        sb.append("📌 Статус: ").append(status).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📊 **Статистика:**\n");
        sb.append("   📋 Взято заявок: ").append(taken).append("\n");
        sb.append("   ✅ Завершено: ").append(completed).append("\n");
        sb.append("   💰 Баллы: ").append(bonus).append("\n");
        sb.append("   📈 Доход: ").append(String.format("%,.0f", revenue)).append(" руб.\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━");

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ =====
        // Добавить балл
        response.addButtonFullRow("➕ Добавить балл", "admin_add_bonus_" + operatorId);
        // Убрать балл
        response.addButtonFullRow("➖ Убрать балл", "admin_remove_bonus_" + operatorId);

        // Блокировка / Разблокировка
        if (operator.getIsActive() != null && operator.getIsActive()) {
            response.addButtonFullRow("🔒 Заблокировать", "admin_block_operator_" + operatorId);
        } else {
            response.addButtonFullRow("🔓 Разблокировать", "admin_unblock_operator_" + operatorId);
        }

        // Удалить
        response.addButtonFullRow("🗑️ Удалить", "admin_delete_operator_" + operatorId);

        response.addButtonFullRow("🔙 Назад", "admin_operators");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }
    /**
     * Добавить балл оператору
     */
    public UniversalResponse addBonusToOperator(Long userId, Long operatorId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "admin_operators");
        }

        operator.setBonusPoints(operator.getBonusPoints() + 1);
        userService.saveUser(operator);

        UniversalResponse response = new UniversalResponse(
                "✅ Оператору **" + operator.getFirstName() + "** добавлен 1 балл.\n" +
                        "💰 Текущий баланс: **" + operator.getBonusPoints() + "** баллов."
        );
        response.addButtonFullRow("🔙 Назад к оператору", "admin_view_operator_" + operatorId);
        response.addButtonFullRow("👥 Список операторов", "admin_operators");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    /**
     * Убрать балл у оператора
     */
    public UniversalResponse removeBonusFromOperator(Long userId, Long operatorId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "admin_operators");
        }

        if (operator.getBonusPoints() <= 0) {
            UniversalResponse response = new UniversalResponse(
                    "❌ У оператора **" + operator.getFirstName() + "** нет баллов для списания."
            );
            response.addButtonFullRow("🔙 Назад к оператору", "admin_view_operator_" + operatorId);
            response.addButtonFullRow("👥 Список операторов", "admin_operators");
            response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        operator.setBonusPoints(operator.getBonusPoints() - 1);
        userService.saveUser(operator);

        UniversalResponse response = new UniversalResponse(
                "✅ У оператора **" + operator.getFirstName() + "** списан 1 балл.\n" +
                        "💰 Текущий баланс: **" + operator.getBonusPoints() + "** баллов."
        );
        response.addButtonFullRow("🔙 Назад к оператору", "admin_view_operator_" + operatorId);
        response.addButtonFullRow("👥 Список операторов", "admin_operators");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Заблокировать оператора
     */
    public UniversalResponse blockOperator(Long userId, Long operatorId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "admin_operators");
        }

        if (operator.getAccessLevel() == AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Нельзя заблокировать администратора.");
        }

        operator.setIsActive(false);
        operator.setIsBlocked(true);
        operator.setBlockedAt(LocalDateTime.now());
        operator.setBlockedReason("Заблокирован администратором");
        userService.saveUser(operator);

        // Уведомляем оператора
        sendNotification(operator.getTelegramId(),
                "🔒 **Ваш аккаунт заблокирован администратором.**\n\n" +
                        "📌 Вы не можете брать новые заявки.\n" +
                        "📌 Для уточнения причин обратитесь к администратору.");

        UniversalResponse response = new UniversalResponse(
                "🔒 Оператор **" + operator.getFirstName() + "** заблокирован."
        );
        response.addButtonFullRow("🔙 Назад к оператору", "admin_view_operator_" + operatorId);
        response.addButtonFullRow("👥 Список операторов", "admin_operators");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    /**
     * Разблокировать оператора
     */
    public UniversalResponse unblockOperator(Long userId, Long operatorId) {
        User admin = getUserOrNull(userId);
        if (!isAdmin(admin)) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "admin_operators");
        }

        operator.setIsActive(true);
        operator.setIsBlocked(false);
        operator.setBlockedAt(null);
        operator.setBlockedReason(null);
        userService.saveUser(operator);

        // Уведомляем оператора
        sendNotification(operator.getTelegramId(),
                "🔓 **Ваш аккаунт разблокирован!**\n\n" +
                        "📌 Теперь вы снова можете брать заявки в работу.\n" +
                        "📌 Хорошей работы!");

        UniversalResponse response = new UniversalResponse(
                "🔓 Оператор **" + operator.getFirstName() + "** разблокирован."
        );
        response.addButtonFullRow("🔙 Назад к оператору", "admin_view_operator_" + operatorId);
        response.addButtonFullRow("👥 Список операторов", "admin_operators");
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
}