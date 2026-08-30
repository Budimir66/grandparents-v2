package org.grandparents.repository;

import org.grandparents.model.OperatorReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperatorReactionRepository extends JpaRepository<OperatorReaction, Long> {
    Optional<OperatorReaction> findByElderIdAndOperatorId(Long elderId, Long operatorId);
    List<OperatorReaction> findByElderId(Long elderId);
    List<OperatorReaction> findByOperatorId(Long operatorId);
    List<OperatorReaction> findByOperatorIdAndReaction(Long operatorId, String reaction);
    /**
     * Возвращает список ID заявок, которые оператор отметил как "Интересные"
     */
    @Query("SELECT r.elderId FROM OperatorReaction r WHERE r.operatorId = :operatorId AND r.reaction = 'INTERESTED'")
    List<Long> findInterestedElderIdsByOperatorId(@Param("operatorId") Long operatorId);

    // OperatorReactionRepository.java
  //  Optional<OperatorReaction> findByOperatorIdAndElderId(Long operatorId, Long elderId);
    // OperatorReactionRepository.java

    @Modifying
    @Query("DELETE FROM Rating r WHERE r.elderId = :elderId")
    void deleteByElderId(@Param("elderId") Long elderId);
    /**
     * Находит ВСЕ реакции оператора на конкретную заявку
     * (возвращает список, потому что могут быть дубликаты)
     */
    @Query("SELECT r FROM OperatorReaction r WHERE r.operatorId = :operatorId AND r.elderId = :elderId")
    List<OperatorReaction> findAllByOperatorIdAndElderId(
            @Param("operatorId") Long operatorId,
            @Param("elderId") Long elderId
    );
    boolean existsByOperatorIdAndElderIdAndReaction(Long operatorId, Long elderId, String reaction);
    @Query("SELECT r.elderId FROM OperatorReaction r WHERE r.operatorId = :operatorId AND r.reaction = 'NOT_INTERESTED'")
    List<Long> findNotInterestedElderIdsByOperatorId(@Param("operatorId") Long operatorId);
}