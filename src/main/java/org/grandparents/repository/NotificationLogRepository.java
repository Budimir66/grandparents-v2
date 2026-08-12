package org.grandparents.repository;

import org.grandparents.model.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByElderId(Long elderId);
    List<NotificationLog> findByOperatorId(Long operatorId);
    long countByOperatorId(Long operatorId);
    long countByOperatorIdAndReaction(Long operatorId, String reaction);
    long countByOperatorIdAndIsReadTrue(Long operatorId);
}