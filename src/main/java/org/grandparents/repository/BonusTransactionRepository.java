package org.grandparents.repository;

import org.grandparents.model.BonusTransaction;
import org.grandparents.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BonusTransactionRepository extends JpaRepository<BonusTransaction, Long> {

    List<BonusTransaction> findByOperatorIdOrderByCreatedAtDesc(Long operatorId);

    List<BonusTransaction> findByType(TransactionType type);

    List<BonusTransaction> findByElderId(Long elderId);
}