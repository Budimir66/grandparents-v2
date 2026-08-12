package org.grandparents.repository;

import org.grandparents.model.AccessLevel;
import org.grandparents.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTelegramId(Long telegramId);

    boolean existsByTelegramId(Long telegramId);

    List<User> findByAccessLevel(AccessLevel accessLevel);

    List<User> findByAccessLevelGreaterThanEqual(AccessLevel accessLevel);

    List<User> findByCareHomeId(Long careHomeId);

    long countByAccessLevel(AccessLevel accessLevel);
    List<User> findAllByAccessLevel(AccessLevel accessLevel);


}