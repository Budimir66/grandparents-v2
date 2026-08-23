package org.grandparents.service;

import org.grandparents.model.AccessLevel;
import org.grandparents.model.User;
import org.grandparents.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<User> findByTelegramId(Long telegramId) {
        return userRepository.findByTelegramId(telegramId);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public List<User> findAllByAccessLevel(AccessLevel accessLevel) {
        return userRepository.findAllByAccessLevel(accessLevel);
    }

    public long countAll() {
        return userRepository.count();
    }

    public long countByAccessLevel(AccessLevel accessLevel) {
        return userRepository.countByAccessLevel(accessLevel);
    }

    public boolean existsByTelegramId(Long telegramId) {
        return userRepository.existsByTelegramId(telegramId);
    }

    public List<User> findByAccessLevel(AccessLevel accessLevel) {
        return userRepository.findByAccessLevel(accessLevel);
    }

    public List<User> findByAccessLevelGreaterThanEqual(AccessLevel accessLevel) {
        return userRepository.findByAccessLevelGreaterThanEqual(accessLevel);
    }

    public List<User> findByCareHomeId(Long careHomeId) {
        return userRepository.findByCareHomeId(careHomeId);
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    public List<User> findByCareHomeIdAndAccessLevel(Long careHomeId, AccessLevel accessLevel) {
        return userRepository.findByCareHomeIdAndAccessLevel(careHomeId, accessLevel);
    }
    private final Map<Long, String> tempPurpose = new ConcurrentHashMap<>();

    public void setTempPurpose(Long userId, String purpose) {
        tempPurpose.put(userId, purpose);
    }

    public String getTempPurpose(Long userId) {
        return tempPurpose.get(userId);
    }

    public void clearTempPurpose(Long userId) {
        tempPurpose.remove(userId);
    }
}