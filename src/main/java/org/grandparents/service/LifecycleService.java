package org.grandparents.service;

import org.grandparents.dto.UniversalResponse;
import org.grandparents.model.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LifecycleService {

    private final ElderService elderService;
    private final UserService userService;
    private final CareHomeService careHomeService;
    private final MessageSender messageSender;

    public LifecycleService(ElderService elderService,
                            UserService userService,
                            CareHomeService careHomeService,
                            MessageSender messageSender) {
        this.elderService = elderService;
        this.userService = userService;
        this.careHomeService = careHomeService;
        this.messageSender = messageSender;
    }

    /**
     * Проверка истекающих заявок — запускается каждый день в 10:00
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void checkExpiringElders() {
        System.out.println("🔍 Проверка истекающих заявок...");

        // 1. Заявки, которые истекают через 3 дня (expires_at <= now() + 3 дня)
        LocalDateTime threeDaysFromNow = LocalDateTime.now().plusDays(3);
        List<Elder> expiringSoon = elderService.findByExpiresAtBeforeAndStatusNot(
                threeDaysFromNow, ElderStatus.COMPLETED
        );

        for (Elder elder : expiringSoon) {
            // Проверяем, что уведомление ещё не отправлено
            if (elder.getReminderSentAt() == null) {
                sendExpirationWarning(elder);
                elder.setReminderSentAt(LocalDateTime.now());
                elderService.updateElder(elder);
                System.out.println("📨 Отправлено уведомление об истечении заявки #" + elder.getId());
            }
        }

        // 2. Заявки, которые уже истекли
        List<Elder> expired = elderService.findByExpiresAtBeforeAndStatusNot(
                LocalDateTime.now(), ElderStatus.COMPLETED
        );

        for (Elder elder : expired) {
            if (elder.getStatus() != ElderStatus.EXPIRED) {
                elder.setStatus(ElderStatus.EXPIRED);
                elderService.updateElder(elder);
                System.out.println("⏰ Заявка #" + elder.getId() + " истекла");
            }
        }
    }

    /**
     * Отправляет уведомление автору о скором истечении заявки
     */
    private void sendExpirationWarning(Elder elder) {
        // Определяем автора
        Long authorId = elder.getCreatedBy() != null ? elder.getCreatedBy() : elder.getClientTelegramId();

        User author = userService.findByTelegramId(authorId).orElse(null);
        if (author == null) {
            System.out.println("⚠️ Автор заявки #" + elder.getId() + " не найден");
            return;
        }

        // Получаем chat_id для отправки
        Long chatId = author.getChatId() != null ? author.getChatId() : authorId;

        String message = "⚠️ **Ваша заявка #" + elder.getId() + " будет удалена через 3 дня!**\n\n" +
                "📋 **Текущие условия:**\n" +
                "👤 **Подопечный:** " + elder.getFullName() + "\n" +
                "💰 **Бюджет:** " + elder.getBudget() + " руб.\n\n" +
                "💡 **Чтобы продлить заявку ещё на 14 дней,** увеличьте бюджет.\n" +
                "Это повысит шансы найти подходящий пансионат.\n\n" +
                "✏️ Нажмите кнопку ниже, чтобы продлить заявку:";

        UniversalResponse response = new UniversalResponse(message);
        response.addButton("✏️ Продлить заявку (увеличить бюджет)", "extend_elder_" + elder.getId());
        response.addButton("📋 Мои заявки", "my_requests");
        response.addButton("🏠 Главное меню", "main_menu");

        messageSender.sendMessage(chatId, response);
        System.out.println("📨 Уведомление об истечении отправлено автору заявки #" + elder.getId());
    }
    /**
     * Проверка подписок пансионатов — запускается каждый день в 10:00
     */
    @Scheduled(cron = "0 0 10 * * ?")
    public void checkSubscriptions() {
        System.out.println("🔍 Проверка подписок пансионатов...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in5Days = now.plusDays(5);
        LocalDateTime in3Days = now.plusDays(3);

        // Находим все активные пансионаты с подпиской
        List<CareHome> careHomes = careHomeService.findAllActiveWithSubscription();

        for (CareHome careHome : careHomes) {
            LocalDateTime endDate = careHome.getSubscriptionEnd();
            if (endDate == null) continue;

            // Проверяем, истекает ли подписка через 5 дней
            if (endDate.isBefore(in5Days) && endDate.isAfter(now)) {
                sendSubscriptionWarning(careHome, 5);
            }

            // Проверяем, истекает ли подписка через 3 дня
            if (endDate.isBefore(in3Days) && endDate.isAfter(now)) {
                sendSubscriptionWarning(careHome, 3);
            }

            // Проверяем, истекла ли подписка
            if (endDate.isBefore(now)) {
                blockCareHome(careHome);
            }
        }
    }
    /**
     * Отправляет уведомление операторам пансионата о скором окончании подписки
     */
    private void sendSubscriptionWarning(CareHome careHome, int days) {
        List<User> operators = userService.findByCareHomeId(careHome.getId());

        if (operators.isEmpty()) {
            System.out.println("⚠️ Нет операторов для пансионата " + careHome.getId());
            return;
        }

        String urgency = days <= 3 ? "❗ СРОЧНО!" : "⚠️";
        String message = urgency + " **Подписка на пансионат истекает!**\n\n" +
                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                "📅 **Подписка истекает через:** " + days + " дней\n" +
                "📆 **Дата окончания:** " + careHome.getSubscriptionEnd() + "\n\n" +
                (days <= 3 ? "🔴 **Срочно продлите подписку!**\n" : "") +
                "После истечения подписки пансионат будет заблокирован.\n" +
                "Операторы не смогут работать с заявками.\n\n" +
                "📞 Для продления свяжитесь с администратором.";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("📋 Мои пансионаты", "my_carehomes");
        response.addButtonFullRow("🏠 Главное меню", "main_menu");

        for (User operator : operators) {
            try {
                Long chatId = operator.getChatId() != null ? operator.getChatId() : operator.getTelegramId();
                messageSender.sendMessage(chatId, response);
                System.out.println("📨 Уведомление о подписке отправлено оператору " + operator.getTelegramId());
            } catch (Exception e) {
                System.err.println("❌ Ошибка отправки оператору " + operator.getTelegramId() + ": " + e.getMessage());
            }
        }

        // Также уведомляем администратора
        notifyAdminAboutSubscription(careHome, days);
    }
    /**
     * Уведомляет администратора об истекающей подписке
     */
    private void notifyAdminAboutSubscription(CareHome careHome, int days) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);

        if (admins.isEmpty()) return;

        String message = "📢 **Подписка истекает!**\n\n" +
                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                "📅 **Истекает через:** " + days + " дней\n" +
                "📆 **Дата окончания:** " + careHome.getSubscriptionEnd() + "\n\n" +
                "Действие: [✅ Продлить подписку]";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Продлить подписку (+30 дней)", "extend_subscription_" + careHome.getId());
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
            } catch (Exception e) {
                System.err.println("❌ Ошибка отправки администратору: " + e.getMessage());
            }
        }
    }
    /**
     * Блокирует пансионат при истечении подписки
     */
    private void blockCareHome(CareHome careHome) {
        System.out.println("🔴 Блокировка пансионата " + careHome.getId() + " (" + careHome.getName() + ")");

        // Обновляем статус пансионата
        careHome.setActive(false);
        careHome.setSubscribed(false);
        careHome.setStatus("INACTIVE");
        careHomeService.save(careHome);

        // Блокируем всех операторов пансионата
        List<User> operators = userService.findByCareHomeId(careHome.getId());

        for (User operator : operators) {
            operator.setIsBlocked(true);
            operator.setBlockedAt(LocalDateTime.now());
            operator.setBlockedReason("Подписка на пансионат истекла");
            userService.saveUser(operator);
            System.out.println("🔴 Оператор " + operator.getTelegramId() + " заблокирован");

            // Отправляем уведомление оператору
            try {
                String message = "❌ **Пансионат заблокирован!**\n\n" +
                        "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                        "📅 **Подписка истекла:** " + careHome.getSubscriptionEnd() + "\n\n" +
                        "🔴 Вы не можете работать с заявками, пока подписка не будет продлена.\n\n" +
                        "📞 Для продления свяжитесь с администратором.";

                UniversalResponse response = new UniversalResponse(message);
                response.addButtonFullRow("📋 Мои пансионаты", "my_carehomes");
                response.addButtonFullRow("🏠 Главное меню", "main_menu");

                Long chatId = operator.getChatId() != null ? operator.getChatId() : operator.getTelegramId();
                messageSender.sendMessage(chatId, response);
            } catch (Exception e) {
                System.err.println("❌ Ошибка отправки оператору: " + e.getMessage());
            }
        }

        // Уведомляем администратора
        notifyAdminAboutBlock(careHome);
    }
    /**
     * Уведомляет администратора о блокировке пансионата
     */
    private void notifyAdminAboutBlock(CareHome careHome) {
        List<User> admins = userService.findByAccessLevel(AccessLevel.ADMIN);

        if (admins.isEmpty()) return;

        String message = "🔴 **Пансионат заблокирован!**\n\n" +
                "🏢 **Пансионат:** " + careHome.getName() + "\n" +
                "📅 **Подписка истекла:** " + careHome.getSubscriptionEnd() + "\n\n" +
                "👥 Операторы пансионата заблокированы.\n" +
                "Для разблокировки продлите подписку.";

        UniversalResponse response = new UniversalResponse(message);
        response.addButtonFullRow("✅ Продлить подписку (+30 дней)", "extend_subscription_" + careHome.getId());
        response.addButtonFullRow("⚙️ Админ-панель", "admin_menu");

        for (User admin : admins) {
            try {
                Long chatId = admin.getChatId() != null ? admin.getChatId() : admin.getTelegramId();
                messageSender.sendMessage(chatId, response);
            } catch (Exception e) {
                System.err.println("❌ Ошибка отправки администратору: " + e.getMessage());
            }
        }
    }
}