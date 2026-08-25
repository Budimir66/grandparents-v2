package org.grandparents.service;

import org.grandparents.model.Rating;
import org.grandparents.model.User;
import org.grandparents.repository.RatingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RatingService {
    private final RatingRepository ratingRepository;
    private final UserService userService;

    public RatingService(RatingRepository ratingRepository, UserService userService) {
        this.ratingRepository = ratingRepository;
        this.userService = userService;
    }

    @Transactional
    public void addRating(Long raterId, Long targetId, Long elderId, Integer stars) {
        // Проверяем, не оценивал ли уже этот оператор эту заявку
        if (ratingRepository.findByRaterIdAndElderId(raterId, elderId).isPresent()) {
            throw new IllegalStateException("Вы уже оценили эту заявку.");
        }

        // Сохраняем оценку
        Rating rating = new Rating();
        rating.setRaterId(raterId);
        rating.setTargetId(targetId);
        rating.setElderId(elderId);
        rating.setStars(stars);
        rating.setCreatedAt(LocalDateTime.now());
        ratingRepository.save(rating);

        // Пересчитываем рейтинг автора
        updateAuthorRating(targetId);

        // Начисляем/списываем баллы
        // userService.findById() возвращает User (не Optional) — убираем .orElse(null)
        User author = userService.findById(targetId);
        if (author != null) {
            if (stars == 5) {
                author.addBonusPoints(1);
            } else if (stars == 1) {
                author.addBonusPoints(-1);
            }
            userService.saveUser(author);
        }
    }
    private void updateAuthorRating(Long targetId) {
        Double avg = ratingRepository.getAverageRatingForUser(targetId);
        Integer total = ratingRepository.getTotalRatingsForUser(targetId);

        // userService.findById() возвращает User (не Optional) — убираем .orElse(null)
        User author = userService.findById(targetId);
        if (author != null) {
            author.setRating(avg != null ? avg : 0.0);
            author.setTotalRatings(total != null ? total : 0);
            userService.saveUser(author);
        }
    }
}