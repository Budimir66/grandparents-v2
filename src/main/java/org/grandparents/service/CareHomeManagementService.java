package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.*;
import org.grandparents.statemachine.DialogState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CareHomeManagementService {

    private static final Logger log = LoggerFactory.getLogger(CareHomeManagementService.class);

    private final UserService userService;
    private final CareHomeService careHomeService;
    private final UserStateService stateService;
    private final YandexMapsService yandexMapsService;
    private final MessageSender messageSender;
    private final ElderService elderService;
    private final InvitationService invitationService;

    public CareHomeManagementService(UserService userService,
                                     CareHomeService careHomeService,
                                     UserStateService stateService,
                                     YandexMapsService yandexMapsService,
                                     MessageSender messageSender,
                                     ElderService elderService,
                                     InvitationService invitationService) {
        this.userService = userService;
        this.careHomeService = careHomeService;
        this.stateService = stateService;
        this.yandexMapsService = yandexMapsService;
        this.messageSender = messageSender;
        this.elderService = elderService;
        this.invitationService = invitationService;
    }

    /**
     * Директор создаёт приглашение оператору
     */
    public UniversalResponse inviteOperator(Long userId) {
        User director = getUserOrNull(userId);
        if (director == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (director.getAccessLevel() != AccessLevel.MANAGER && director.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Только директор может приглашать операторов.");
        }

        // Получаем все пансионаты директора
        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
        if (director.getCareHomeId() != null) {
            CareHome ch = careHomeService.findById(director.getCareHomeId());
            if (ch != null && !careHomes.contains(ch)) {
                careHomes.add(ch);
            }
        }

        if (careHomes.isEmpty()) {
            return responseWithMainMenu("❌ У вас нет пансионатов для приглашения оператора.");
        }

        // Если только 1 пансионат — сразу создаём приглашение
        if (careHomes.size() == 1) {
            return createInvitation(userId, careHomes.get(0).getId());
        }

        // Если несколько — показываем список
        UniversalResponse response = new UniversalResponse(
                "🏢 **Выберите пансионат для оператора:**\n\n" +
                        "Оператор будет видеть заявки только этого пансионата.\n\n" +
                        "🌐 Или выберите **«Вся сеть»** — оператор будет работать со всеми вашими пансионатами."
        );

        for (CareHome ch : careHomes) {
            response.addButtonFullRow("🏢 " + ch.getName(), "invite_carehome_" + ch.getId());
        }

        response.addButtonFullRow("🌐 Вся сеть", "invite_carehome_all");
        response.addButtonFullRow("❌ Отменить", "cancel_action");
        return response;
    }

    /**
     * Создаёт приглашение и показывает кодовое слово
     */
    /**
     * Создаёт приглашение и показывает кодовое слово
     */
    public UniversalResponse createInvitation(Long directorId, Long careHomeId) {
        User director = getUserOrNull(directorId);
        if (director == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        Invitation invitation = invitationService.createInvitation(careHomeId, director.getId());

        String careHomeName = "ВСЯ СЕТЬ";
        if (careHomeId != null && careHomeId > 0) {
            CareHome ch = careHomeService.findById(careHomeId);
            if (ch != null) {
                careHomeName = ch.getName();
            }
        }

        int randomCode = 1000 + new Random().nextInt(9000);
        String token = careHomeName + " " + randomCode;

        String message = """
            ✅ Приглашение создано!

            🏢 Пансионат: **%s**
            📅 Действительно: 3 дня

            🔑 Токен: `%s`
            Для оператора: скопировать Токен из личного чата и вставить в чат бота "ПансАльянс"

            📤 Отправьте этот токен оператору.
            """.formatted(careHomeName, token);

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("📋 Список операторов", "manager_operators");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ПРОСМОТР ПАНСИОНАТА =====
    // ============================================================

    public UniversalResponse viewCarehome(Long userId, Long careHomeId) {
        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "list_carehomes");
        }

        String card = buildCarehomeCard(careHome);
        UniversalResponse response = new UniversalResponse(card);

        // Кнопка с картой, если есть координаты
        if (careHome.getLatitude() != null && careHome.getLongitude() != null) {
            String mapLink = "https://yandex.ru/maps/?pt=" +
                    careHome.getLongitude() + "," + careHome.getLatitude() + "&z=17";
            response.addUrlButtonFullRow("🗺️ Показать на Яндекс.Карте", mapLink);
        }

        response.addButtonFullRow("📋 Назад к списку", "list_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ПРЕДЛОЖЕНИЕ ПАНСИОНАТА =====
    // ============================================================

    /**
     * Начать предложение пансионата (для OPERATOR, MANAGER, ADMIN)
     */
    public UniversalResponse startProposeCarehome(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        // ===== РАЗРЕШАЕМ OPERATOR, MANAGER и ADMIN =====
        if (!isOperator(user) && !isManager(user) && !isAdmin(user)) {
            return responseWithMainMenu("❌ Только операторы, директора и администраторы могут предлагать пансионаты.");
        }

        stateService.setState(userId, DialogState.AWAITING_CAREHOME_NAME_PROPOSAL);
        UniversalResponse response = new UniversalResponse(
                "🏢 **Предложение пансионата**\n\n" +
                        "Вы можете предложить пансионат для регистрации в системе.\n" +
                        "После проверки администратор одобрит или отклонит заявку.\n\n" +
                        "📝 Введите **название** пансионата:"
        );
        response.addButtonFullRow("❌ Отменить", "cancel_action");
        return response;
    }
    // ============================================================
    // ===== СПИСОК ПАНСИОНАТОВ =====
    // ============================================================

    public UniversalResponse listCarehomes(Long userId) {
        List<CareHome> careHomes = careHomeService.findActive();

        UniversalResponse response = new UniversalResponse(
                "🏢 **Список пансионатов**\n\nВыберите пансионат для просмотра:"
        );

        // ===== КНОПКА "ПРЕДЛОЖИТЬ ПАНСИОНАТ" (СВЕРХУ, НА ВСЮ СТРОКУ) =====
        response.addButtonFullRow("➕ Предложить пансионат", "propose_carehome_client");

        if (careHomes.isEmpty()) {
            response.setText("🏢 **Пансионаты**\n\nАктивных пансионатов пока нет.\n\n" +
                    "Вы можете первым предложить пансионат для регистрации!");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        for (CareHome careHome : careHomes) {
            String priceText = (careHome.getPriceFrom() > 0) ? "от " + careHome.getPriceFrom() + " руб." : "";
            String buttonText = careHome.getName() + (priceText.isEmpty() ? "" : " (" + priceText + ")");
            response.addButtonFullRow(buttonText, "view_carehome_" + careHome.getId());
        }

        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== КАРТА ПАНСИОНАТОВ =====
    // ============================================================

    public UniversalResponse mapCarehomes(Long userId) {
        List<CareHome> careHomes = careHomeService.findActive();
        List<CareHome> mappedCareHomes = careHomes.stream()
                .filter(ch -> ch.getLatitude() != null && ch.getLongitude() != null)
                .collect(Collectors.toList());

        if (mappedCareHomes.isEmpty()) {
            return responseWithBackAndMainMenu("❌ Нет пансионатов с координатами.", "list_carehomes");
        }

        StringBuilder mapUrl = new StringBuilder("https://yandex.ru/maps/?");
        for (int i = 0; i < mappedCareHomes.size(); i++) {
            CareHome ch = mappedCareHomes.get(i);
            if (i > 0) {
                mapUrl.append("&pt=");
            } else {
                mapUrl.append("pt=");
            }
            mapUrl.append(ch.getLongitude()).append(",").append(ch.getLatitude())
                    .append(",pm2rdm~");
        }
        String url = mapUrl.toString().replaceAll("~$", "") + "&z=11";

        UniversalResponse response = new UniversalResponse(
                "🗺️ **Открыть карту пансионатов**\n\n" +
                        "📍 " + url
        );
        response.addButtonFullRow("📋 Назад", "list_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    // ============================================================
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    // ============================================================

    private String buildCarehomeCard(CareHome careHome) {
        String card = "🏢 **" + careHome.getName() + "**\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📍 **Адрес:** " + careHome.getAddress() + "\n" +
                "📞 **Телефон:** " + careHome.getPhone() + "\n" +
                "💰 **Цена от:** " + careHome.getPriceFrom() + " руб.\n" +
                "📝 **Описание:** " + careHome.getDescription() + "\n" +
                "🏥 **Специализация:** " + careHome.getSpecialization() + "\n";

        if (careHome.getWebsite() != null && !careHome.getWebsite().isEmpty() && !careHome.getWebsite().equals("-")) {
            card += "🌐 **Сайт:** " + careHome.getWebsite() + "\n";
        }

        card += "━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "📌 **Статус:** " + getCareHomeStatusIcon(careHome.getStatus()) + " " + careHome.getStatus();

        return card;
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

    private User getUserOrNull(Long userId) {
        return userService.findByTelegramId(userId).orElse(null);
    }

    private boolean isOperator(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.OPERATOR;
    }

    private boolean isAdmin(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.ADMIN;
    }

    private UniversalResponse responseWithMainMenu(String text) {
        UniversalResponse response = new UniversalResponse(text);
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    private UniversalResponse responseWithBackAndMainMenu(String text, String backCallback) {
        UniversalResponse response = new UniversalResponse(text);
        if (backCallback != null) {
            response.addButtonFullRow("🔙 Назад", backCallback);
        }
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }

    private void notifyAdminsAboutNewCarehome(CareHome careHome) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);

        if (admins.isEmpty()) {
            log.warn("⚠️ Нет администраторов для уведомления");
            return;
        }

        User operator = getUserOrNull(careHome.getProposedBy());
        String operatorName = operator != null ? operator.getFirstName() : "Неизвестный";

        String message = "📢 **Новый пансионат на модерацию!**\n\n" +
                "🏢 **Название:** " + careHome.getName() + "\n" +
                "📍 **Адрес:** " + careHome.getAddress() + "\n" +
                "📞 **Телефон:** " + careHome.getPhone() + "\n" +
                "💰 **Цена от:** " + careHome.getPriceFrom() + " руб.\n" +
                "📝 **Описание:** " + careHome.getDescription() + "\n" +
                "🏥 **Специализация:** " + careHome.getSpecialization() + "\n" +
                "👤 **Предложил:** " + operatorName + "\n\n" +
                "Выберите действие:";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Одобрить", "approve_carehome_" + careHome.getId());
        response.addButtonFullRow("❌ Отклонить", "reject_carehome_" + careHome.getId());
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
                log.info("📨 Уведомление отправлено администратору {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("❌ Ошибка отправки администратору {}: {}", admin.getTelegramId(), e.getMessage(), e);
            }
        }
    }
    /**
     * Начать предложение пансионата (для клиентов)
     */
    public UniversalResponse startProposeCarehomeClient(Long userId) {
        stateService.setState(userId, DialogState.AWAITING_CAREHOME_NAME_PROPOSAL);
        UniversalResponse response = new UniversalResponse(
                "🏢 **Предложение пансионата**\n\n" +
                        "Вы можете предложить пансионат для регистрации в системе.\n" +
                        "После проверки администратор одобрит или отклонит заявку.\n\n" +
                        "📝 Введите **название** пансионата:"
        );
        response.addButtonFullRow("❌ Отменить", "cancel_action");
        return response;
    }
    /**
     * Сохраняет предложенный пансионат после принятия оферты
     */
    public UniversalResponse saveProposedCarehome(Long userId) {
        String name = stateService.getTempCareHomeName(userId);
        String address = stateService.getTempCareHomeAddress(userId);
        String phone = stateService.getTempCareHomePhone(userId);
        Double priceValue = stateService.getTempCareHomePrice(userId);
        String description = stateService.getTempCareHomeDescription(userId);
        String specialization = stateService.getTempCareHomeSpecialization(userId);
        String website = stateService.getTempCareHomeWebsite(userId);

        if (name == null || name.trim().isEmpty()) {
            return responseWithMainMenu("❌ Ошибка: название пансионата не заполнено.");
        }

        CareHome careHome = new CareHome();
        careHome.setName(name);
        careHome.setAddress(address);
        careHome.setPhone(phone);
        careHome.setPriceFrom(priceValue);
        careHome.setDescription(description);
        careHome.setSpecialization(specialization);
        if (website != null && !website.equals("-")) {
            careHome.setWebsite(website);
        }
        careHome.setStatus("PENDING");
        User user = userService.findByTelegramId(userId).orElse(null);
        if (user != null) {
            careHome.setProposedBy(user.getId());  // сохраняем id пользователя (8)
        }
        careHome.setProposedAt(LocalDateTime.now());
        careHome.setActive(false);
        careHome.setSubscribed(false);
        careHome.setOfferAccepted(true);  // ← НОВОЕ ПОЛЕ (добавить в модель)
        careHome.setOfferAcceptedAt(LocalDateTime.now());
        careHomeService.save(careHome);

        stateService.clearState(userId);

        // Геокодируем адрес
        try {
            double[] coords = yandexMapsService.geocodeAddress(address);
            if (coords != null) {
                careHome.setLatitude(coords[0]);
                careHome.setLongitude(coords[1]);
                careHomeService.save(careHome);
                log.info("✅ Координаты сохранены для {}", name);
            }
        } catch (Exception e) {
            log.error("❌ Ошибка геокодирования: {}", e.getMessage());
        }

        notifyAdminsAboutNewCarehome(careHome);

        UniversalResponse response = new UniversalResponse(
                "✅ **Пансионат отправлен на модерацию!**\n\n" +
                        "🏢 **Название:** " + name + "\n" +
                        "📍 **Адрес:** " + address + "\n" +
                        "📞 **Телефон:** " + phone + "\n" +
                        "💰 **Цена от:** " + priceValue + " руб.\n" +
                        "📝 **Описание:** " + description + "\n" +
                        "🏥 **Специализация:** " + specialization + "\n" +
                        "🌐 **Сайт:** " + (website != null && !website.equals("-") ? website : "не указан") + "\n" +
                        "📋 **Оферта принята:** ✅\n\n" +
                        "⏳ Статус: На модерации\n\n" +
                        "Администратор рассмотрит заявку в ближайшее время."
        );
        response.addButtonFullRow("🏢 Список пансионатов", "list_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает карточку пансионата для MANAGER (и ADMIN)
     */
    public UniversalResponse showCarehomeCardForManager(Long userId, Long careHomeId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "my_carehomes");
        }

        // ===== ПРОВЕРЯЕМ ДОСТУП =====
        boolean isManager = user.getAccessLevel() == AccessLevel.MANAGER;
        boolean isAdmin = user.getAccessLevel() == AccessLevel.ADMIN;
        boolean isOperator = user.getAccessLevel() == AccessLevel.OPERATOR;
        boolean isOwner = careHome.getProposedBy() != null && careHome.getProposedBy().equals(userId);
        boolean isAssigned = user.getCareHomeId() != null && user.getCareHomeId().equals(careHomeId);

        if (!isAdmin && !isOwner && !isAssigned) {
            return responseWithMainMenu("❌ У вас нет доступа к этому пансионату.");
        }

        // ===== ФОРМИРУЕМ КАРТОЧКУ =====
        String statusIcon = getCareHomeStatusIcon(careHome.getStatus());
        String activeStatus = careHome.getIsActive() != null && careHome.getIsActive() ? "🟢 Активен" : "🔴 Неактивен";
        String subscriptionStatus = careHome.getIsSubscribed() != null && careHome.getIsSubscribed()
                ? "✅ Активна" : "❌ Неактивна";

        StringBuilder sb = new StringBuilder();
        sb.append("🏢 **").append(careHome.getName()).append("**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📍 **Адрес:** ").append(careHome.getAddress()).append("\n");
        sb.append("📞 **Телефон:** ").append(careHome.getPhone()).append("\n");

        if (careHome.getWebsite() != null && !careHome.getWebsite().isEmpty() && !careHome.getWebsite().equals("-")) {
            sb.append("🌐 **Сайт:** ").append(careHome.getWebsite()).append("\n");
        }

        sb.append("💰 **Цена от:** ").append(careHome.getPriceFrom()).append(" руб.\n");
        sb.append("📝 **Описание:** ").append(careHome.getDescription()).append("\n");
        sb.append("🏥 **Специализация:** ").append(careHome.getSpecialization()).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("📌 **Статус:** ").append(statusIcon).append(" ").append(careHome.getStatus()).append("\n");
        sb.append("🔘 **Активен:** ").append(activeStatus).append("\n");
        sb.append("📅 **Подписка:** ").append(subscriptionStatus).append("\n");

        if (careHome.getSubscriptionEnd() != null) {
            long daysLeft = java.time.Duration.between(LocalDateTime.now(), careHome.getSubscriptionEnd()).toDays();
            sb.append("📆 **До:** ").append(careHome.getSubscriptionEnd())
                    .append(" (осталось ").append(Math.max(0, daysLeft)).append(" дн.)\n");
        }

        if (careHome.getLatitude() != null && careHome.getLongitude() != null) {
            sb.append("🗺️ **Координаты:** ").append(careHome.getLatitude())
                    .append(", ").append(careHome.getLongitude()).append("\n");
        }
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━");

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ В ЗАВИСИМОСТИ ОТ РОЛИ =====
        if (isOperator) {
            // ===== ДЛЯ ОПЕРАТОРА: ТОЛЬКО "ГЛАВНОЕ МЕНЮ" =====
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
        } else {
            // ===== ДЛЯ MANAGER И ADMIN: ПОЛНЫЙ НАБОР КНОПОК =====
            response.addButtonFullRow("📝 Редактировать", "manager_carehome_edit_" + careHome.getId());
            response.addButtonFullRow("🗑️ Удалить", "manager_carehome_delete_" + careHome.getId());
            response.addButtonFullRow("👥 Операторы", "carehome_operators_" + careHome.getId());
            response.addButtonFullRow("📊 Статистика", "manager_stats_for_carehome_" + careHome.getId());
            response.addButtonFullRow("🔙 Мои пансионаты", "my_carehomes");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
        }

        return response;
    }
    /**
     * Удаление пансионата (для MANAGER и ADMIN)
     */
    /**
     * Удаление пансионата (для MANAGER и ADMIN) после подтверждения
     */
    public UniversalResponse deleteCarehomeForManager(Long userId, Long careHomeId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ У вас нет прав на удаление пансионата.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "my_carehomes");
        }

        boolean isOwner = careHome.getProposedBy() != null && careHome.getProposedBy().equals(userId);
        boolean isAdmin = user.getAccessLevel() == AccessLevel.ADMIN;

        if (!isAdmin && !isOwner) {
            return responseWithMainMenu("❌ Вы не можете удалить этот пансионат.");
        }

        // Проверяем, есть ли у пансионата операторы
        List<User> operators = userService.findByCareHomeId(careHomeId);
        if (!operators.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "❌ **Нельзя удалить пансионат!**\n\n" +
                            "У пансионата есть зарегистрированные операторы (" + operators.size() + " чел.).\n\n" +
                            "Сначала удалите всех операторов."
            );
            response.addButtonFullRow("🔙 Назад к пансионату", "view_my_carehome_" + careHomeId);
            response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // ===== УДАЛЯЕМ ПАНСИОНАТ =====
        careHomeService.delete(careHomeId);
        log.info("🗑️ Пансионат {} (ID: {}) удалён пользователем {}", careHome.getName(), careHomeId, userId);

        UniversalResponse response = new UniversalResponse(
                "✅ **Пансионат \"" + careHome.getName() + "\" успешно удалён!**"
        );
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Запрос подтверждения удаления пансионата
     */
    public UniversalResponse confirmDeleteCarehome(Long userId, Long careHomeId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ У вас нет прав на удаление пансионата.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "my_carehomes");
        }

        UniversalResponse response = new UniversalResponse(
                "⚠️ **Вы уверены, что хотите удалить пансионат?**\n\n" +
                        "🏢 **Название:** " + careHome.getName() + "\n" +
                        "📍 **Адрес:** " + careHome.getAddress() + "\n\n" +
                        "Это действие нельзя отменить!\n\n" +
                        "❗ Если у пансионата есть операторы, удаление будет невозможно."
        );
        response.addButtonFullRow("✅ Да, удалить", "manager_carehome_delete_confirm_" + careHomeId);
        response.addButtonFullRow("❌ Отменить", "view_my_carehome_" + careHomeId);
        return response;
    }
    /**
     * Начать редактирование пансионата (для MANAGER)
     */
    public UniversalResponse startEditCarehome(Long userId, Long careHomeId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ У вас нет прав на редактирование.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "my_carehomes");
        }

        // Проверяем, что пользователь является владельцем (для MANAGER)
        boolean isOwner = careHome.getProposedBy() != null && careHome.getProposedBy().equals(userId);
        boolean isAdmin = user.getAccessLevel() == AccessLevel.ADMIN;

        if (!isAdmin && !isOwner) {
            return responseWithMainMenu("❌ Вы не можете редактировать этот пансионат.");
        }

        // Сохраняем ID пансионата в состояние
        stateService.setEditingCareHomeId(userId, careHomeId);
        stateService.setEditingCareHome(userId, careHome);
        stateService.setState(userId, DialogState.ADMIN_EDIT_CAREHOME_NAME);

        UniversalResponse response = new UniversalResponse(
                "✏️ **Редактирование пансионата**\n\n" +
                        "🏢 **Текущее название:** " + careHome.getName() + "\n\n" +
                        "Введите **новое название** (или нажмите 'Оставить без изменений'):"
        );
        response.addButton("⏭️ Оставить без изменений", "skip_edit_carehome");
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }
    /**
     * Сохранить изменения пансионата и отправить на модерацию
     */
    public UniversalResponse saveEditedCarehome(Long userId) {
        CareHome editCareHome = stateService.getEditingCareHome(userId);
        if (editCareHome == null) {
            Long editId = stateService.getEditingCareHomeId(userId);
            editCareHome = careHomeService.findById(editId);
            if (editCareHome == null) {
                stateService.clearState(userId);
                return responseWithMainMenu("❌ Пансионат не найден.");
            }
            stateService.setEditingCareHome(userId, editCareHome);
        }

        // Сохраняем изменения в БД
        careHomeService.save(editCareHome);

        // Очищаем состояние
        stateService.clearState(userId);
        stateService.clearEditingCareHome(userId);

        // Уведомляем администраторов (только если это MANAGER)
        if (userService.findByTelegramId(userId).orElse(null).getAccessLevel() == AccessLevel.MANAGER) {
            notifyAdminsAboutCarehomeEdit(editCareHome, userId);
        }

        UniversalResponse response = new UniversalResponse(
                "✅ **Изменения сохранены!**\n\n" +
                        "📋 Данные пансионата обновлены."
        );
        response.addButtonFullRow("🔙 Назад к пансионату", "view_my_carehome_" + editCareHome.getId());
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Уведомить администраторов об изменении пансионата
     */
    private void notifyAdminsAboutCarehomeEdit(CareHome careHome, Long managerId) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);
        if (admins.isEmpty()) {
            log.warn("⚠️ Нет администраторов для уведомления");
            return;
        }

        User manager = userService.findByTelegramId(managerId).orElse(null);
        String managerName = manager != null ? manager.getFirstName() : "Неизвестный";

        String message = "📢 **Запрос на изменение пансионата!**\n\n" +
                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                "👤 **Запросил:** " + managerName + "\n" +
                "📌 **Статус:** Ожидает модерации\n\n" +
                "🔄 Изменения требуют проверки администратором.\n\n" +
                "Выберите действие:";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Одобрить изменения", "approve_carehome_edit_" + careHome.getId());
        response.addButtonFullRow("❌ Отклонить изменения", "reject_carehome_edit_" + careHome.getId());
        response.addButtonFullRow("📋 Просмотреть пансионат", "admin_carehome_view_" + careHome.getId());

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
                log.info("📨 Уведомление отправлено администратору {}", admin.getTelegramId());
            } catch (Exception e) {
                log.error("❌ Ошибка отправки администратору: {}", e.getMessage(), e);
            }
        }
    }
    /**
     * Показывает список пансионатов для MANAGER
     */
    public UniversalResponse showMyCarehomes(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        // ===== РАЗРЕШАЕМ: MANAGER, OPERATOR, ADMIN =====
        if (user.getAccessLevel() != AccessLevel.MANAGER &&
                user.getAccessLevel() != AccessLevel.OPERATOR &&
                user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        List<CareHome> careHomes = new ArrayList<>();

        // ===== ДЛЯ ОПЕРАТОРА: ТОЛЬКО СВОЙ ПАНСИОНАТ =====
        if (user.getAccessLevel() == AccessLevel.OPERATOR) {
            if (user.getCareHomeId() != null) {
                CareHome careHome = careHomeService.findById(user.getCareHomeId());
                if (careHome != null) {
                    careHomes.add(careHome);
                }
            }
        } else {
            // ===== ДЛЯ MANAGER И ADMIN: ВСЕ ПАНСИОНАТЫ =====
            careHomes = careHomeService.findByProposedBy(userId);
            if (user.getCareHomeId() != null) {
                CareHome careHome = careHomeService.findById(user.getCareHomeId());
                if (careHome != null && !careHomes.contains(careHome)) {
                    careHomes.add(careHome);
                }
            }
        }

        if (careHomes.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "🏢 **Мои пансионаты**\n\n" +
                            "У вас пока нет пансионатов.\n\n" +
                            "📝 Предложите пансионат для регистрации в системе."
            );
            response.addButtonFullRow("📝 Предложить пансионат", "propose_carehome");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        UniversalResponse response = new UniversalResponse("🏢 **Мои пансионаты**\n\nВыберите пансионат:");

        for (CareHome careHome : careHomes) {
            String statusIcon = getCareHomeStatusIcon(careHome.getStatus());
            String buttonText = statusIcon + " " + careHome.getName();
            response.addButtonFullRow(buttonText, "view_my_carehome_" + careHome.getId());
        }

        // ===== ДЛЯ ОПЕРАТОРА: НЕТ КНОПКИ "ПРЕДЛОЖИТЬ ПАНСИОНАТ" =====
        if (user.getAccessLevel() != AccessLevel.OPERATOR) {
            response.addButtonFullRow("📝 Предложить пансионат", "propose_carehome");
        }

        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    private boolean isManager(User user) {
        return user != null && user.getAccessLevel() == AccessLevel.MANAGER;
    }
    /**
     * Показывает список всех операторов MANAGER (по всем пансионатам)
     */
    public UniversalResponse showOperatorsListForManager(Long userId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        // Получаем все пансионаты MANAGER
        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
        if (user.getCareHomeId() != null) {
            CareHome careHome = careHomeService.findById(user.getCareHomeId());
            if (careHome != null && !careHomes.contains(careHome)) {
                careHomes.add(careHome);
            }
        }

        if (careHomes.isEmpty()) {
            UniversalResponse response = new UniversalResponse(
                    "👥 **Операторы**\n\n" +
                            "У вас пока нет пансионатов.\n\n" +
                            "📝 Сначала зарегистрируйте пансионат."
            );
            response.addButtonFullRow("➕ Пригласить оператора", "invite_operator");
            response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // Собираем всех операторов со всех пансионатов
        List<User> allOperators = new ArrayList<>();
        for (CareHome careHome : careHomes) {
            List<User> operators = userService.findByCareHomeId(careHome.getId());
            allOperators.addAll(operators);
        }

        // Убираем дубликаты (если оператор работает в нескольких пансионатах)
        Set<Long> operatorIds = new HashSet<>();
        List<User> uniqueOperators = allOperators.stream()
                .filter(op -> operatorIds.add(op.getId()))
                .collect(Collectors.toList());

        UniversalResponse response;

        if (uniqueOperators.isEmpty()) {
            response = new UniversalResponse(
                    "👥 **Операторы**\n\n" +
                            "У вас пока нет зарегистрированных операторов.\n\n" +
                            "📝 Зарегистрируйте первого оператора!"
            );
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("👥 **Операторы (всех пансионатов)**\n\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("📌 Всего операторов: ").append(uniqueOperators.size()).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            response = new UniversalResponse(sb.toString());

            // ===== КНОПКИ ДЛЯ КАЖДОГО ОПЕРАТОРА (БЕЗ ИКОНОК) =====
            for (User operator : uniqueOperators) {
                String careHomeName = "не указан";
                if (operator.getCareHomeId() != null) {
                    CareHome ch = careHomeService.findById(operator.getCareHomeId());
                    if (ch != null) {
                        careHomeName = ch.getName();
                    }
                }
                // БЕЗ ИКОНОК! Только текст
                String buttonText = operator.getFirstName() + " (" + careHomeName + ")";
                response.addButtonFullRow(buttonText, "view_operator_" + operator.getId());
            }
        }

        // ===== КНОПКА РЕГИСТРАЦИИ ОПЕРАТОРА =====
        response.addButtonFullRow("➕ Пригласить оператора", "invite_operator");
      //  response.addButtonFullRow("📝 Регистрация оператора", "register_operator");
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает список операторов конкретного пансионата
     */
    public UniversalResponse showOperatorsForCarehome(Long userId, Long careHomeId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        CareHome careHome = careHomeService.findById(careHomeId);
        if (careHome == null) {
            return responseWithBackAndMainMenu("❌ Пансионат не найден.", "my_carehomes");
        }

        // Проверяем доступ
        boolean isOwner = careHome.getProposedBy() != null && careHome.getProposedBy().equals(userId);
        boolean isAdmin = user.getAccessLevel() == AccessLevel.ADMIN;
        if (!isAdmin && !isOwner) {
            return responseWithMainMenu("❌ У вас нет доступа к этому пансионату.");
        }

        // Получаем операторов пансионата
        List<User> operators = userService.findByCareHomeId(careHomeId);

        UniversalResponse response;

        if (operators.isEmpty()) {
            response = new UniversalResponse(
                    "👥 **Операторы пансионата**\n\n" +
                            "🏢 " + careHome.getName() + "\n\n" +
                            "У пансионата пока нет операторов.\n\n" +
                            "📝 Зарегистрируйте первого оператора!"
            );
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("👥 **Операторы пансионата**\n\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("🏢 ").append(careHome.getName()).append("\n");
            sb.append("📌 Всего операторов: ").append(operators.size()).append("\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

            response = new UniversalResponse(sb.toString());

            // ===== КНОПКИ ДЛЯ КАЖДОГО ОПЕРАТОРА (БЕЗ ИКОНОК) =====
            for (User operator : operators) {
                String buttonText = operator.getFirstName();
                if (operator.getIsActive() != null && operator.getIsActive()) {
                    buttonText += " 🟢";
                } else {
                    buttonText += " 🔴";
                }
                response.addButtonFullRow(buttonText, "view_operator_" + operator.getId());
            }
        }

        // ===== КНОПКИ =====
        response.addButtonFullRow("📝 Регистрация оператора", "register_operator");
        response.addButtonFullRow("🔙 Назад к пансионату", "view_my_carehome_" + careHomeId);
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Показывает карточку оператора со статистикой
     */
    public UniversalResponse showOperatorCard(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        // ===== ИЩЕМ ПО ID, А НЕ ПО TELEGRAM ID =====
        User operator = userService.findById(operatorId);  // ← ИЗМЕНЕНО!
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "manager_operators");
        }

        // Проверяем, что оператор принадлежит MANAGER
        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
        boolean isOperatorBelongsToManager = careHomes.stream()
                .anyMatch(ch -> ch.getId().equals(operator.getCareHomeId()));

        if (!isOperatorBelongsToManager && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Этот оператор не принадлежит вашим пансионатам.");
        }

        // ===== ФОРМИРУЕМ КАРТОЧКУ =====
        StringBuilder sb = new StringBuilder();
        sb.append("👤 **Карточка оператора**\n\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        sb.append("👤 Имя: ").append(operator.getFirstName()).append("\n");
        sb.append("📱 Телефон: ").append(operator.getPhone() != null ? operator.getPhone() : "не указан").append("\n");

        String careHomeName = "не указан";
        if (operator.getCareHomeId() != null) {
            CareHome ch = careHomeService.findById(operator.getCareHomeId());
            if (ch != null) {
                careHomeName = ch.getName();
            }
        }
        sb.append("🏢 Пансионат: ").append(careHomeName).append("\n");

        String status = operator.getIsActive() != null && operator.getIsActive() ? "🟢 Активен" : "🔴 Неактивен";
        sb.append("📌 Статус: ").append(status).append("\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");

        // ===== СТАТИСТИКА ОПЕРАТОРА =====
        int completed = operator.getTotalCompleted() != null ? operator.getTotalCompleted() : 0;
        int taken = operator.getTotalTaken() != null ? operator.getTotalTaken() : 0;
        int bonus = operator.getBonusPoints();
        double revenue = operator.getTotalRevenue() != null ? operator.getTotalRevenue() : 0.0;

        sb.append("📊 **Статистика оператора:**\n");
        sb.append("   📋 Взято заявок: ").append(taken).append("\n");
        sb.append("   ✅ Завершено: ").append(completed).append("\n");
        sb.append("   💰 Баллы: ").append(bonus).append("\n");
        sb.append("   📈 Доход: ").append(String.format("%,.0f", revenue)).append(" руб.\n");

        UniversalResponse response = new UniversalResponse(sb.toString());

        // ===== КНОПКИ =====
        response.addButtonFullRow("✏️ Редактировать", "edit_operator_" + operator.getId());
        response.addButtonFullRow("🗑️ Удалить", "confirm_delete_operator_" + operator.getId());
        response.addButtonFullRow("🔙 Назад", "manager_operators");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        return response;
    }
    /**
     * Запрос подтверждения удаления оператора
     */
    public UniversalResponse confirmDeleteOperator(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "manager_operators");
        }

        // Проверяем, что оператор принадлежит MANAGER
        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
        boolean isOperatorBelongsToManager = careHomes.stream()
                .anyMatch(ch -> ch.getId().equals(operator.getCareHomeId()));

        if (!isOperatorBelongsToManager && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Этот оператор не принадлежит вашим пансионатам.");
        }

        UniversalResponse response = new UniversalResponse(
                "⚠️ **Вы уверены, что хотите удалить оператора?**\n\n" +
                        "👤 **Имя:** " + operator.getFirstName() + "\n" +
                        "📱 **Телефон:** " + (operator.getPhone() != null ? operator.getPhone() : "не указан") + "\n" +
                        "🏢 **Пансионат:** " + getCareHomeName(operator.getCareHomeId()) + "\n\n" +
                        "✅ **Завершено заявок:** " + (operator.getTotalCompleted() != null ? operator.getTotalCompleted() : 0) + "\n" +
                        "💰 **Баллов:** " + operator.getBonusPoints() + "\n\n" +
                        "❗ Это действие нельзя отменить!"
        );
        response.addButtonFullRow("✅ Да, удалить", "delete_operator_confirm_" + operatorId);
        response.addButtonFullRow("❌ Отменить", "view_operator_" + operatorId);
        return response;
    }
    /**
     * Удаление оператора (после подтверждения)
     */
    public UniversalResponse deleteOperator(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "manager_operators");
        }

        // Проверяем, что оператор принадлежит MANAGER
        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
        boolean isOperatorBelongsToManager = careHomes.stream()
                .anyMatch(ch -> ch.getId().equals(operator.getCareHomeId()));

        if (!isOperatorBelongsToManager && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Этот оператор не принадлежит вашим пансионатам.");
        }

        // Проверяем, есть ли у оператора активные заявки в работе
        List<Elder> activeElders = elderService.findByAssignedOperatorId(operator.getTelegramId());
        long inProgress = activeElders.stream()
                .filter(e -> e.getStatus() == ElderStatus.IN_PROGRESS)
                .count();

        if (inProgress > 0) {
            UniversalResponse response = new UniversalResponse(
                    "❌ **Нельзя удалить оператора!**\n\n" +
                            "У оператора есть активные заявки в работе (" + inProgress + " шт.).\n\n" +
                            "Сначала завершите или передайте заявки другому оператору."
            );
            response.addButtonFullRow("🔙 Назад к оператору", "view_operator_" + operatorId);
            response.addButtonFullRow("👥 Список операторов", "manager_operators");
            response.addButtonFullRow("🏠 Главное меню", "main_menu");
            return response;
        }

        // Сохраняем имя для уведомления
        String operatorName = operator.getFirstName();

        // Удаляем оператора
        userService.deleteUser(operatorId);
        log.info("🗑️ Оператор {} (ID: {}) удалён пользователем {}", operatorName, operatorId, userId);

        UniversalResponse response = new UniversalResponse(
                "✅ **Оператор \"" + operatorName + "\" успешно удалён!**"
        );
        response.addButtonFullRow("👥 Список операторов", "manager_operators");
        response.addButtonFullRow("🏢 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");
        return response;
    }
    /**
     * Получить название пансионата по ID
     */
    private String getCareHomeName(Long careHomeId) {
        if (careHomeId == null) {
            return "не указан";
        }
        CareHome careHome = careHomeService.findById(careHomeId);
        return careHome != null ? careHome.getName() : "не указан";
    }
    /**
     * Начать редактирование оператора (MANAGER/ADMIN)
     */
    public UniversalResponse startEditOperator(Long userId, Long operatorId) {
        User user = getUserOrNull(userId);
        if (user == null) {
            return responseWithMainMenu("❌ Пользователь не найден.");
        }

        if (user.getAccessLevel() != AccessLevel.MANAGER && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Доступ запрещён.");
        }

        User operator = userService.findById(operatorId);
        if (operator == null) {
            return responseWithBackAndMainMenu("❌ Оператор не найден.", "manager_operators");
        }

        // Проверяем, что оператор принадлежит MANAGER
        List<CareHome> careHomes = careHomeService.findByProposedBy(userId);
        boolean isOperatorBelongsToManager = careHomes.stream()
                .anyMatch(ch -> ch.getId().equals(operator.getCareHomeId()));

        if (!isOperatorBelongsToManager && user.getAccessLevel() != AccessLevel.ADMIN) {
            return responseWithMainMenu("❌ Этот оператор не принадлежит вашим пансионатам.");
        }

        // Сохраняем ID оператора в состояние
        stateService.setEditingOperatorId(userId, operatorId);
        stateService.setState(userId, DialogState.EDITING_OPERATOR_NAME);

        UniversalResponse response = new UniversalResponse(
                "✏️ **Редактирование оператора**\n\n" +
                        "👤 Текущее имя: " + operator.getFirstName() + "\n" +
                        "📱 Текущий телефон: " + (operator.getPhone() != null ? operator.getPhone() : "не указан") + "\n\n" +
                        "Введите **новое имя** оператора (или нажмите 'Оставить без изменений'):"
        );
        response.addButton("⏭️ Оставить без изменений", "skip_edit_operator");
        response.addButton("❌ Отменить", "cancel_action");
        return response;
    }

}