package org.grandparents.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Пансионат — организация, предоставляющая услуги по уходу за пожилыми людьми.
 * В пансионате работают операторы, и в него заселяются подопечные.
 */
@Entity
@Table(name = "care_home")
public class CareHome {

    // ===== ПЕРВИЧНЫЙ КЛЮЧ =====
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "address")
    private String address;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "price_from")
    private Double priceFrom;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "specialization")
    private String specialization;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "is_subscribed")
    private Boolean isSubscribed = false;

    @Column(name = "subscription_start")
    private LocalDateTime subscriptionStart;

    @Column(name = "subscription_end")
    private LocalDateTime subscriptionEnd;

    @Column(name = "available_places")
    private Integer availablePlaces;

    @Column(name = "capacity")
    private Integer capacity;

    // ===== НОВЫЕ ПОЛЯ =====
    @Column(name = "status")
    private String status;  // PENDING, APPROVED, REJECTED, INACTIVE

    @Column(name = "proposed_by")
    private Long proposedBy;

    @Column(name = "proposed_at")
    private LocalDateTime proposedAt;

    @Column(name = "moderated_by")
    private Long moderatedBy;

    @Column(name = "moderated_at")
    private LocalDateTime moderatedAt;

    @Column(name = "moderator_comment")
    private String moderatorComment;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "website")
    private String website;

    @Column(name = "offer_accepted")
    private Boolean offerAccepted = false;

    @Column(name = "offer_accepted_at")
    private LocalDateTime offerAcceptedAt;

    @Column(name = "monthly_revenue")
    private Double monthlyRevenue = 0.0;

  //  @Column(name = "status")
  //  private String status;  // PENDING, APPROVED, REJECTED, INACTIVE, PENDING_MODERATION

    @Column(name = "pending_changes", columnDefinition = "TEXT")
    private String pendingChanges;  // JSON с изменениями

    @Column(name = "moderation_requested_at")
    private LocalDateTime moderationRequestedAt;



    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "care_home_photos", joinColumns = @JoinColumn(name = "care_home_id"))
    @Column(name = "photo_url")
    private List<String> photos = new ArrayList<>();
    // ===== СПИСОК СОТРУДНИКОВ (храним как строку) =====
    // ВАЖНО: Для простоты хранения списка операторов используем строку с разделителями,
    // либо пока просто оставляем поле без привязки к базе.
    // На начальном этапе мы будем хранить операторов отдельно, поэтому это поле пока не сохраняем.
    @Transient
    private List<Long> operatorIds = new ArrayList<>();

    // ===== КОНСТРУКТОРЫ =====
    public CareHome() {
    }

    public CareHome(String name, String address, String phone, String email,
                    double priceFrom, int capacity, String description) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.priceFrom = priceFrom;
        this.capacity = capacity;
        this.availablePlaces = capacity;
        this.description = description;
        this.rating = 0.0;
        this.isActive = true;
        this.isSubscribed = false;
    }



    // ===== ГЕТТЕРЫ И СЕТТЕРЫ =====

    public Double getMonthlyRevenue() { return monthlyRevenue; }
    public void setMonthlyRevenue(Double monthlyRevenue) { this.monthlyRevenue = monthlyRevenue; }

    public Boolean getOfferAccepted() { return offerAccepted; }
    public void setOfferAccepted(Boolean offerAccepted) { this.offerAccepted = offerAccepted; }

    public LocalDateTime getOfferAcceptedAt() { return offerAcceptedAt; }
    public void setOfferAcceptedAt(LocalDateTime offerAcceptedAt) { this.offerAcceptedAt = offerAcceptedAt; }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getIsSubscribed() {
        return isSubscribed;
    }

    public void setIsSubscribed(Boolean isSubscribed) {
        this.isSubscribed = isSubscribed;
    }

    // Геттер
    public Boolean getIsActive() {
        return isActive;
    }

    // Сеттер
    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProposedBy() {
        return proposedBy;
    }

    public void setProposedBy(Long proposedBy) {
        this.proposedBy = proposedBy;
    }

    public LocalDateTime getProposedAt() {
        return proposedAt;
    }

    public void setProposedAt(LocalDateTime proposedAt) {
        this.proposedAt = proposedAt;
    }

    public Long getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(Long moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public LocalDateTime getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(LocalDateTime moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public String getModeratorComment() {
        return moderatorComment;
    }

    public void setModeratorComment(String moderatorComment) {
        this.moderatorComment = moderatorComment;
    }

    // (все методы такие же, как в твоём классе, я их не менял)
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public double getPriceFrom() { return priceFrom; }
    public void setPriceFrom(double priceFrom) { this.priceFrom = priceFrom; }

    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }

    public int getAvailablePlaces() { return availablePlaces; }
    public void setAvailablePlaces(int availablePlaces) { this.availablePlaces = availablePlaces; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public List<String> getPhotos() {
        return photos;
    }

    public void setPhotos(List<String> photos) {
        this.photos = photos;
    }


    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public LocalDateTime getSubscriptionStart() { return subscriptionStart; }
    public void setSubscriptionStart(LocalDateTime subscriptionStart) { this.subscriptionStart = subscriptionStart; }

    public LocalDateTime getSubscriptionEnd() { return subscriptionEnd; }
    public void setSubscriptionEnd(LocalDateTime subscriptionEnd) {
        this.subscriptionEnd = subscriptionEnd;
        if (subscriptionEnd != null) {
            this.isSubscribed = LocalDateTime.now().isBefore(subscriptionEnd);
        } else {
            this.isSubscribed = false;
        }
    }

    public boolean isSubscribed() { return isSubscribed; }
    public void setSubscribed(boolean subscribed) { isSubscribed = subscribed; }

    public List<Long> getOperatorIds() { return operatorIds; }
    public void setOperatorIds(List<Long> operatorIds) { this.operatorIds = operatorIds; }

    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С СОТРУДНИКАМИ =====
    public boolean addOperator(Long operatorId) {
        if (operatorId == null || operatorIds.contains(operatorId)) {
            return false;
        }
        operatorIds.add(operatorId);
        return true;
    }

    public boolean removeOperator(Long operatorId) {
        return operatorIds.remove(operatorId);
    }

    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С МЕСТАМИ =====
    public boolean hasAvailablePlaces() {
        return availablePlaces > 0;
    }

    public void incrementAvailablePlaces() {
        if (availablePlaces < capacity) {
            availablePlaces++;
        }
    }

    public boolean decrementAvailablePlaces() {
        if (availablePlaces <= 0) {
            return false;
        }
        availablePlaces--;
        return true;
    }

    // ===== МЕТОДЫ ДЛЯ РАБОТЫ С ПОДПИСКОЙ =====
    public void activateSubscription(int months) {
        LocalDateTime now = LocalDateTime.now();
        this.subscriptionStart = now;
        this.subscriptionEnd = now.plusMonths(months);
        this.isSubscribed = true;
    }

    public void renewSubscription(int months) {
        if (isSubscribed && subscriptionEnd != null) {
            this.subscriptionEnd = subscriptionEnd.plusMonths(months);
        } else {
            activateSubscription(months);
        }
        this.isSubscribed = true;
    }

    public void deactivateSubscription() {
        this.isSubscribed = false;
        this.subscriptionEnd = null;
    }

    public long getDaysUntilSubscriptionEnd() {
        if (!isSubscribed || subscriptionEnd == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(subscriptionEnd)) {
            return 0;
        }
        return java.time.Duration.between(now, subscriptionEnd).toDays();
    }

    public boolean canAcceptRequests() {
        return isActive && isSubscribed && hasAvailablePlaces();
    }

    @Override
    public String toString() {
        return "CareHome{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", priceFrom=" + priceFrom +
                ", availablePlaces=" + availablePlaces +
                ", isSubscribed=" + isSubscribed +
                '}';
    }
    public void addPhoto(String photoUrl) {
        if (this.photos == null) {
            this.photos = new ArrayList<>();
        }
        if (this.photos.size() < 5) {
            this.photos.add(photoUrl);
        }
    }
    // Метод для добавления дохода
    public void addMonthlyRevenue(Double amount) {
        if (this.monthlyRevenue == null) this.monthlyRevenue = 0.0;
        this.monthlyRevenue += amount;
    }

}