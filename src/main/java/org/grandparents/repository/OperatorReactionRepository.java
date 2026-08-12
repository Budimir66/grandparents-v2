package org.grandparents.repository;

import org.grandparents.model.OperatorReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperatorReactionRepository extends JpaRepository<OperatorReaction, Long> {
    Optional<OperatorReaction> findByElderIdAndOperatorId(Long elderId, Long operatorId);
    List<OperatorReaction> findByElderId(Long elderId);
    List<OperatorReaction> findByOperatorId(Long operatorId);
    List<OperatorReaction> findByOperatorIdAndReaction(Long operatorId, String reaction);
}