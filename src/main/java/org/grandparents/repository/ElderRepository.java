package org.grandparents.repository;

import org.grandparents.model.Elder;
import org.grandparents.model.ElderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ElderRepository extends JpaRepository<Elder, Long> {

    // ===== ПОИСК ПО СТАТУСАМ =====
    List<Elder> findByStatus(ElderStatus status);
    List<Elder> findByStatusIn(List<ElderStatus> statuses);  // ← ДОБАВИТЬ
    List<Elder> findByStatusNotIn(List<ElderStatus> statuses);

    // ===== ПОИСК ПО ПОЛЬЗОВАТЕЛЯМ =====
    List<Elder> findByClientTelegramId(Long clientTelegramId);
    List<Elder> findByCreatedBy(Long createdBy);
    List<Elder> findByAssignedOperatorId(Long assignedOperatorId);
    List<Elder> findByCompletedBy(Long completedBy);
    List<Elder> findByOfferedBy(Long offeredBy);
    List<Elder> findByAcceptedBy(Long acceptedBy);
    List<Elder> findByCareHomeId(Long careHomeId);  // ← ДОБАВИТЬ

    // ===== ПОИСК ПО ЛОКАЦИИ И БЮДЖЕТУ =====
    List<Elder> findByPreferredLocationContainingIgnoreCase(String location);
    List<Elder> findByBudgetGreaterThanEqual(double budget);

    // ===== ПОИСК ПО ДАТАМ =====
    List<Elder> findByStatusAndCreatedAtBefore(ElderStatus status, LocalDateTime date);
    List<Elder> findByStatusInAndCreatedAtBefore(List<ElderStatus> statuses, LocalDateTime date);
    List<Elder> findByExpiresAtBeforeAndStatusNot(LocalDateTime date, ElderStatus status);

    // ===== СТАТИСТИКА =====
    long countByCreatedBy(Long createdBy);
    long countByStatus(ElderStatus status);
    long countByStatusIn(List<ElderStatus> statuses);

    @Query("SELECT COUNT(e) FROM Elder e WHERE e.status IN :statuses")
    long countActiveElders(@Param("statuses") List<ElderStatus> statuses);
}