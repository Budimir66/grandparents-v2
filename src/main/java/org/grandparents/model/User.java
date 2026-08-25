package org.grandparents.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Пользователь системы — один Telegram аккаунт.
 * Уровень доступа определяет, что может делать пользователь.
 */
@Entity
@Table(name =  "\"user\"")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_level", nullable = false, length = 20)
    private AccessLevel accessLevel = AccessLevel.GUEST;

    @Column(name = "care_home_id")
    private Long careHomeId;  // Только для OPERATOR и выше

    @Column(name = "registered_at")
    private LocalDateTime registeredAt;

    @Column(name = "is_registered")
    private Boolean isRegistered = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // ===== ПОЛЯ ДЛЯ ОПЕРАТОРОВ =====
    @Column(name = "bonus_points")
    private Integer bonusPoints = 0;

    @Column(name = "total_sent_offers")
    private Integer totalSentOffers = 0;

    @Column(name = "total_accepted_offers")
    private Integer totalAcceptedOffers = 0;

    @Column(name = "total_completed_offers")
    private Integer totalCompletedOffers = 0;

    @Column(name = "is_subscribed")
    private Boolean isSubscribed = false;

    @Column(name = "subscription_end")
    private LocalDateTime subscriptionEnd;

    // ===== ПОЛЯ ДЛЯ КЛИЕНТОВ =====
    @Column(name = "anonymous_requests_count")
    private Integer anonymousRequestsCount = 0;

    @Column(name = "preferred_location")
    private String preferredLocation;

    @Column(name = "completed_this_month")
    private Integer completedThisMonth = 0;

    @Column(name = "current_month")
    private Integer currentMonth = 0;

    @Column(name = "chat_id")
    private Long chatId;

    // Настройки оператора
    @Column(name = "preferred_city")
    private String preferredCity;

    @Column(name = "preferred_region")
    private String preferredRegion;

    @Column(name = "budget_min")
    private Double budgetMin;

    @Column(name = "budget_max")
    private Double budgetMax;

    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = true;

    @Column(name = "notify_from")
    private String notifyFrom;  // или LocalTime

    @Column(name = "notify_to")
    private String notifyTo;    // или LocalTime

    // Статистика оператора
    @Column(name = "total_notifications_received")
    private Integer totalNotificationsReceived = 0;

    @Column(name = "total_interested")
    private Integer totalInterested = 0;

    @Column(name = "total_not_interested")
    private Integer totalNotInterested = 0;

    @Column(name = "total_views")
    private Integer totalViews = 0;

    @Column(name = "total_taken")
    private Integer totalTaken = 0;

    @Column(name = "total_completed")
    private Integer totalCompleted = 0;

    @Column(name = "total_earned_bonus")
    private Integer totalEarnedBonus = 0;

    @Column(name = "is_blocked")
    private Boolean isBlocked = false;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Column(name = "blocked_reason")
    private String blockedReason;

    @Column(name = "total_revenue")
    private Double totalRevenue = 0.0;

    @Column(name = "price")
    private Double price;  // цена заселения (финальная стоимость)

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "email")
    private String email;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "rating")
    private Double rating = 0.0;

    @Column(name = "total_ratings")
    private Integer totalRatings = 0;

    // ===== КОНСТРУКТОРЫ =====
    public User() {
    }

    public User(Long telegramId, String firstName) {
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.registeredAt = LocalDateTime.now();
        this.accessLevel = org.grandparents.model.AccessLevel.GUEST;
        this.isRegistered = false;
    }

    // ===== ГЕТТЕРЫ И СЕТТЕРЫ =====

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Boolean getIsSubscribed() {
        return isSubscribed;
    }

    public void setIsSubscribed(Boolean isSubscribed) {
        this.isSubscribed = isSubscribed;
    }

    public Boolean getIsRegistered() {
        return isRegistered;
    }

    public void setIsRegistered(Boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

    public String getWhatsapp() { return whatsapp; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }

    public String getTelegramUsername() { return telegramUsername; }
    public void setTelegramUsername(String telegramUsername) { this.telegramUsername = telegramUsername; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Double getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(Double totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public Double getRating() {
        return rating != null ? rating : 0.0;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Integer getTotalRatings() {
        return totalRatings != null ? totalRatings : 0;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }

    public void incrementTotalRatings() {
        if (this.totalRatings == null) {
            this.totalRatings = 0;
        }
        this.totalRatings++;
    }



    // Геттер и сеттер
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Boolean getIsBlocked() {
        return isBlocked;
    }

    public void setIsBlocked(Boolean isBlocked) {
        this.isBlocked = isBlocked;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Integer getTotalTaken() {
        return totalTaken;
    }

    public void setTotalTaken(Integer totalTaken) {
        this.totalTaken = totalTaken;
    }

    public Integer getTotalCompleted() {
        return totalCompleted;
    }

    public void setTotalCompleted(Integer totalCompleted) {
        this.totalCompleted = totalCompleted;
    }

    public Integer getTotalEarnedBonus() {
        return totalEarnedBonus;
    }

    public void setTotalEarnedBonus(Integer totalEarnedBonus) {
        this.totalEarnedBonus = totalEarnedBonus;
    }

    public Integer getCurrentMonth() {
        return currentMonth;
    }

    public void setCurrentMonth(Integer currentMonth) {
        this.currentMonth = currentMonth;
    }

    public String getPreferredCity() {
        return preferredCity;
    }

    public void setPreferredCity(String preferredCity) {
        this.preferredCity = preferredCity;
    }

    public String getPreferredRegion() {
        return preferredRegion;
    }

    public void setPreferredRegion(String preferredRegion) {
        this.preferredRegion = preferredRegion;
    }

    public Double getBudgetMin() {
        return budgetMin;
    }

    public void setBudgetMin(Double budgetMin) {
        this.budgetMin = budgetMin;
    }

    public Double getBudgetMax() {
        return budgetMax;
    }

    public void setBudgetMax(Double budgetMax) {
        this.budgetMax = budgetMax;
    }

    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getNotifyFrom() {
        return notifyFrom;
    }

    public void setNotifyFrom(String notifyFrom) {
        this.notifyFrom = notifyFrom;
    }

    public String getNotifyTo() {
        return notifyTo;
    }

    public void setNotifyTo(String notifyTo) {
        this.notifyTo = notifyTo;
    }

    public Integer getTotalNotificationsReceived() {
        return totalNotificationsReceived;
    }

    public void setTotalNotificationsReceived(Integer totalNotificationsReceived) {
        this.totalNotificationsReceived = totalNotificationsReceived;
    }

    public Integer getTotalInterested() {
        return totalInterested;
    }

    public void setTotalInterested(Integer totalInterested) {
        this.totalInterested = totalInterested;
    }

    public Integer getTotalNotInterested() {
        return totalNotInterested;
    }

    public void setTotalNotInterested(Integer totalNotInterested) {
        this.totalNotInterested = totalNotInterested;
    }

    public Integer getTotalViews() {
        return totalViews;
    }

    public void setTotalViews(Integer totalViews) {
        this.totalViews = totalViews;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTelegramId() { return telegramId; }
    public void setTelegramId(Long telegramId) { this.telegramId = telegramId; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public org.grandparents.model.AccessLevel getAccessLevel() { return accessLevel; }
    public void setAccessLevel(org.grandparents.model.AccessLevel accessLevel) { this.accessLevel = accessLevel; }

    public Long getCareHomeId() { return careHomeId; }
    public void setCareHomeId(Long careHomeId) { this.careHomeId = careHomeId; }

    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(LocalDateTime registeredAt) { this.registeredAt = registeredAt; }

    public boolean isRegistered() {return isRegistered != null && isRegistered;}
    public void setRegistered(boolean registered) { isRegistered = registered; }

    public boolean isActive() {return isActive != null && isActive;}
    public void setActive(boolean active) { isActive = active; }

    public int getBonusPoints() { return bonusPoints; }
    public void setBonusPoints(int bonusPoints) { this.bonusPoints = bonusPoints; }
    public void addBonusPoints(int amount) { this.bonusPoints += amount; }
    public boolean spendBonusPoints(int amount) {
        if (this.bonusPoints < amount) return false;
        this.bonusPoints -= amount;
        return true;
    }
    /**
     * Проверяет, есть ли у пользователя достаточно баллов
     * @param amount необходимое количество баллов
     * @return true, если баллов достаточно
     */
    public boolean hasEnoughBonusPoints(int amount) {
        return this.bonusPoints >= amount;
    }
    public int getTotalSentOffers() { return totalSentOffers; }
    public void setTotalSentOffers(int totalSentOffers) { this.totalSentOffers = totalSentOffers; }
    public void incrementSentOffers() { this.totalSentOffers++; }

    public int getTotalAcceptedOffers() { return totalAcceptedOffers; }
    public void setTotalAcceptedOffers(int totalAcceptedOffers) { this.totalAcceptedOffers = totalAcceptedOffers; }
    public void incrementAcceptedOffers() { this.totalAcceptedOffers++; }

    public int getTotalCompletedOffers() { return totalCompletedOffers; }
    public void setTotalCompletedOffers(int totalCompletedOffers) { this.totalCompletedOffers = totalCompletedOffers; }
    public void incrementCompletedOffers() { this.totalCompletedOffers++; }

    public boolean isSubscribed() {return isSubscribed != null && isSubscribed;}
    public void setSubscribed(boolean subscribed) { isSubscribed = subscribed; }

    public LocalDateTime getSubscriptionEnd() { return subscriptionEnd; }
    public void setSubscriptionEnd(LocalDateTime subscriptionEnd) {
        this.subscriptionEnd = subscriptionEnd;
        if (subscriptionEnd != null) {
            this.isSubscribed = LocalDateTime.now().isBefore(subscriptionEnd);
        } else {
            this.isSubscribed = false;
        }
    }

    public int getAnonymousRequestsCount() { return anonymousRequestsCount; }
    public void setAnonymousRequestsCount(int anonymousRequestsCount) { this.anonymousRequestsCount = anonymousRequestsCount; }
    public void incrementAnonymousRequestsCount() {
        if (this.anonymousRequestsCount == null) {
            this.anonymousRequestsCount = 0;
        }
        this.anonymousRequestsCount++;
    }

    public String getPreferredLocation() { return preferredLocation; }
    public void setPreferredLocation(String preferredLocation) { this.preferredLocation = preferredLocation; }
    /**
     * Увеличивает счётчик закрытых заявок за месяц
     */
    public void incrementCompletedThisMonth() {
        if (this.completedThisMonth == null) {
            this.completedThisMonth = 0;
        }
        if (this.currentMonth == null) {
            this.currentMonth = LocalDateTime.now().getMonthValue();
        }
        this.completedThisMonth++;
    }

    /**
     * Сбрасывает счётчик закрытых заявок за месяц,
     * если наступил новый месяц
     */
    public void resetMonthlyCounterIfNeeded() {
        int currentMonthValue = LocalDateTime.now().getMonthValue();
        if (this.currentMonth == null || this.currentMonth != currentMonthValue) {
            this.completedThisMonth = 0;
            this.currentMonth = currentMonthValue;
        }
    }

    /**
     * Получить количество закрытых заявок в текущем месяце
     */
    public int getCompletedThisMonth() {
        if (this.completedThisMonth == null) {
            return 0;
        }
        return this.completedThisMonth;
    }

    public void setCompletedThisMonth(Integer completedThisMonth) {
        this.completedThisMonth = completedThisMonth;
    }
    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    public void incrementTotalTaken() {
        if (this.totalTaken == null) this.totalTaken = 0;
        this.totalTaken++;
    }

    public void incrementTotalCompleted() {
        if (this.totalCompleted == null) this.totalCompleted = 0;
        this.totalCompleted++;
    }

    public void addEarnedBonus(int points) {
        if (this.totalEarnedBonus == null) this.totalEarnedBonus = 0;
        this.totalEarnedBonus += points;
    }

    public String getFullName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName;
    }

    public boolean hasAccess(org.grandparents.model.AccessLevel required) {
        return this.accessLevel.hasAccess(required);
    }

    public boolean canCreateAnonymousRequest() {
        if (isRegistered) return true;
        return anonymousRequestsCount < 2;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", telegramId=" + telegramId +
                ", firstName='" + firstName + '\'' +
                ", accessLevel=" + accessLevel +
                ", careHomeId=" + careHomeId +
                '}';
    }
    public boolean isAdmin() {
        return this.accessLevel == AccessLevel.ADMIN;
    }
    // Метод для добавления дохода
    public void addRevenue(Double amount) {
        if (this.totalRevenue == null) this.totalRevenue = 0.0;
        this.totalRevenue += amount;
    }
}