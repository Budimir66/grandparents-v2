package org.grandparents.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "elder")
public class Elder {

    // ===== ОСНОВНЫЕ ПОЛЯ =====
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_telegram_id", nullable = false)
    private Long clientTelegramId;

    @Column(name = "client_first_name")
    private String clientFirstName;

    @Column(name = "client_phone")
    private String clientPhone;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "age")
    private int age;

    @Column(name = "health_condition")
    private String healthCondition;

    @Column(name = "budget")
    private double budget;

    @Column(name = "preferred_location")
    private String preferredLocation;

    @Column(name = "requirements")
    private String requirements;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ElderStatus status;

    @Column(name = "is_anonymous")
    private boolean isAnonymous;

    // ===== ПОЛЯ ДЛЯ ОПЕРАТОРОВ =====
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "care_home_id")
    private Long careHomeId;

    @Column(name = "offered_by")
    private Long offeredBy;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "completed_by")
    private Long completedBy;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "additional_comments", columnDefinition = "TEXT")
    private String additionalComments;

    // ===== ДАТЫ =====
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "edit_count")
    private Integer editCount = 0;

    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @Column(name = "city")
    private String city;

    @Column(name = "region")
    private String region;

    @Column(name = "assigned_operator_id")
    private Long assignedOperatorId;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "price")
    private Double price;  // цена заселения (финальная стоимость)

    @Column(name = "consent_given")
    private Boolean consentGiven = false;

    @Column(name = "consent_given_at")
    private LocalDateTime consentGivenAt;

    // ===== СОГЛАСИЕ НА ПОЛУЧЕНИЕ СООБЩЕНИЙ =====
    @Column(name = "consent_calls")
    private Boolean consentCalls = false;

    @Column(name = "consent_messages")
    private Boolean consentMessages = false;

    @Column(name = "consent_newsletter")
    private Boolean consentNewsletter = false;

    // ===== КОНСТРУКТОРЫ =====
    public Elder() {
    }

    public Elder(Long clientTelegramId, String clientFirstName, String fullName,
                 int age, String healthCondition, double budget,
                 String preferredLocation, String requirements, boolean isAnonymous) {
        this.clientTelegramId = clientTelegramId;
        this.clientFirstName = clientFirstName;
        this.fullName = fullName;
        this.age = age;
        this.healthCondition = healthCondition;
        this.budget = budget;
        this.preferredLocation = preferredLocation;
        this.requirements = requirements;
        this.isAnonymous = isAnonymous;
        this.status = ElderStatus.NEW;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ===== ГЕТТЕРЫ И СЕТТЕРЫ =====

    public Boolean getConsentCalls() { return consentCalls; }
    public void setConsentCalls(Boolean consentCalls) { this.consentCalls = consentCalls; }

    public Boolean getConsentMessages() { return consentMessages; }
    public void setConsentMessages(Boolean consentMessages) { this.consentMessages = consentMessages; }

    public Boolean getConsentNewsletter() { return consentNewsletter; }
    public void setConsentNewsletter(Boolean consentNewsletter) { this.consentNewsletter = consentNewsletter; }


    public Boolean getConsentGiven() { return consentGiven; }
    public void setConsentGiven(Boolean consentGiven) { this.consentGiven = consentGiven; }

    public LocalDateTime getConsentGivenAt() { return consentGivenAt; }
    public void setConsentGivenAt(LocalDateTime consentGivenAt) { this.consentGivenAt = consentGivenAt; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getClientTelegramId() { return clientTelegramId; }
    public void setClientTelegramId(Long clientTelegramId) { this.clientTelegramId = clientTelegramId; }

    public String getClientFirstName() { return clientFirstName; }
    public void setClientFirstName(String clientFirstName) { this.clientFirstName = clientFirstName; }

    public String getClientPhone() { return clientPhone; }
    public void setClientPhone(String clientPhone) { this.clientPhone = clientPhone; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getHealthCondition() { return healthCondition; }
    public void setHealthCondition(String healthCondition) { this.healthCondition = healthCondition; }

    public double getBudget() { return budget; }
    public void setBudget(double budget) { this.budget = budget; }

    public String getPreferredLocation() { return preferredLocation; }
    public void setPreferredLocation(String preferredLocation) { this.preferredLocation = preferredLocation; }

    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }

    public ElderStatus getStatus() { return status; }
    public void setStatus(ElderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isAnonymous() { return isAnonymous; }
    public void setAnonymous(boolean anonymous) { isAnonymous = anonymous; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getCareHomeId() { return careHomeId; }
    public void setCareHomeId(Long careHomeId) { this.careHomeId = careHomeId; }

    public Long getOfferedBy() { return offeredBy; }
    public void setOfferedBy(Long offeredBy) { this.offeredBy = offeredBy; }

    public Long getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(Long acceptedBy) {
        this.acceptedBy = acceptedBy;
        this.acceptedAt = LocalDateTime.now();
    }

    public Long getCompletedBy() { return completedBy; }
    public void setCompletedBy(Long completedBy) {
        this.completedBy = completedBy;
        this.completedAt = LocalDateTime.now();
    }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getAdditionalComments() { return additionalComments; }
    public void setAdditionalComments(String additionalComments) { this.additionalComments = additionalComments; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    public Integer getEditCount() { return editCount; }
    public void setEditCount(Integer editCount) { this.editCount = editCount; }

    public LocalDateTime getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(LocalDateTime reminderSentAt) { this.reminderSentAt = reminderSentAt; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Long getAssignedOperatorId() { return assignedOperatorId; }
    public void setAssignedOperatorId(Long assignedOperatorId) { this.assignedOperatorId = assignedOperatorId; }

    public LocalDateTime getTakenAt() { return takenAt; }
    public void setTakenAt(LocalDateTime takenAt) { this.takenAt = takenAt; }

    // ===== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =====
    public boolean isVisibleToOthers() {
        return status != ElderStatus.ACCEPTED
                && status != ElderStatus.COMPLETED
                && status != ElderStatus.NEW;
    }

    public boolean canBeOffered() {
        return status == ElderStatus.NEW || status == ElderStatus.NEW;
    }

    public boolean canBeAccepted() {
        return status == ElderStatus.OFFERED || status == ElderStatus.NEW;
    }

    public boolean isFourteenDaysPassed() {
        if (acceptedAt == null) return false;
        return LocalDateTime.now().isAfter(acceptedAt.plusDays(14));
    }

    public boolean isBlocked() {
        if (acceptedAt == null) return false;
        return LocalDateTime.now().isBefore(acceptedAt.plusHours(24));
    }

    @Override
    public String toString() {
        return "Elder{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", age=" + age +
                ", budget=" + budget +
                ", status=" + status +
                '}';
    }
}