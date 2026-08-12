package org.grandparents.service;
import org.grandparents.model.*;
import org.grandparents.repository.BonusTransactionRepository;
import org.grandparents.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BonusService {

    private final BonusTransactionRepository bonusTransactionRepository;
    private final UserRepository userRepository;

    public BonusService(BonusTransactionRepository bonusTransactionRepository,
                        UserRepository userRepository) {
        this.bonusTransactionRepository = bonusTransactionRepository;
        this.userRepository = userRepository;
    }

    /**
     * Начислить баллы оператору
     */
    public void addBonus(Long operatorId, int amount, String description) {
        User user = findUser(operatorId);
        user.addBonusPoints(amount);
        userRepository.save(user);

        BonusTransaction transaction = new BonusTransaction(
                operatorId, null, TransactionType.EARN,
                amount, description, user.getBonusPoints()
        );
        bonusTransactionRepository.save(transaction);
    }

    /**
     * Списать баллы у оператора
     */
    public boolean spendBonus(Long operatorId, int amount, String description) {
        User user = findUser(operatorId);
        if (!user.hasEnoughBonusPoints(amount)) {
            return false;
        }
        user.spendBonusPoints(amount);
        userRepository.save(user);

        BonusTransaction transaction = new BonusTransaction(
                operatorId, null, TransactionType.SPEND,
                -amount, description, user.getBonusPoints()
        );
        bonusTransactionRepository.save(transaction);
        return true;
    }

    /**
     * Получить историю транзакций оператора
     */
    public List<BonusTransaction> getTransactionHistory(Long operatorId) {
        return bonusTransactionRepository.findByOperatorIdOrderByCreatedAtDesc(operatorId);
    }

    /**
     * Проверить бонус за 5 закрытых заявок в месяц
     */
    public void checkMonthlyBonus(Long operatorId) {
        User user = findUser(operatorId);
        user.resetMonthlyCounterIfNeeded();

        if (user.getCompletedThisMonth() >= 5) {
            // Начисляем бонус
            user.addBonusPoints(3);
            user.setCompletedThisMonth(0);
            userRepository.save(user);

            BonusTransaction transaction = new BonusTransaction(
                    operatorId, null, TransactionType.BONUS,
                    3, "Бонус за 5 закрытых заявок в месяце", user.getBonusPoints()
            );
            bonusTransactionRepository.save(transaction);
        }
    }

    /**
     * Начислить стартовый бонус при регистрации
     */
    public void giveInitialBonus(Long operatorId) {
        User user = findUser(operatorId);
        user.addBonusPoints(10);
        userRepository.save(user);

        BonusTransaction transaction = new BonusTransaction(
                operatorId, null, TransactionType.INIT,
                10, "Стартовый бонус", user.getBonusPoints()
        );
        bonusTransactionRepository.save(transaction);
    }

    /**
     * Найти пользователя по ID
     */
    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}