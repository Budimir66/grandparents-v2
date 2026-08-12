package org.grandparents.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * История транзакций баллов оператора.
 * Каждая операция с баллами сохраняется для прозрачности.
 */
@Entity
@Table(name = "bonus_transaction")
public class BonusTransaction {

    // ===== ПЕРВИЧНЫЙ КЛЮЧ =====
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== ПОЛЯ =====
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "elder_id")
    private Long elderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ===== КОНСТРУКТОРЫ =====
    public BonusTransaction() {
    }

    public BonusTransaction(Long operatorId, Long elderId, TransactionType type,
                            int amount, String description, int balanceAfter) {
        this.operatorId = operatorId;
        this.elderId = elderId;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.balanceAfter = balanceAfter;
        this.createdAt = LocalDateTime.now();
    }

    // ===== ГЕТТЕРЫ И СЕТТЕРЫ =====
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Long getElderId() {
        return elderId;
    }

    public void setElderId(Long elderId) {
        this.elderId = elderId;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(int balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ===== ВСПОМОГАТЕЛЬНЫЙ МЕТОД =====
    public String getFormattedAmount() {
        return amount > 0 ? "+" + amount : String.valueOf(amount);
    }

    @Override
    public String toString() {
        return "BonusTransaction{" +
                "id=" + id +
                ", operatorId=" + operatorId +
                ", elderId=" + elderId +
                ", type=" + type +
                ", amount=" + amount +
                ", balanceAfter=" + balanceAfter +
                ", createdAt=" + createdAt +
                '}';
    }
}