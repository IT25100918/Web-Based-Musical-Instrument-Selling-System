package com.instrumentstore.patterns.strategy.review;

import com.instrumentstore.model.Review;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** ConcreteStrategy: best ratings first (ties: newest first). */
@Component
public class HighestRatedStrategy implements ReviewSortStrategy {

    @Override
    public String getKey() { return "highest"; }

    @Override
    public String getLabel() { return "Highest rating"; }

    @Override
    public List<Review> sort(List<Review> reviews) {
        return reviews.stream()
                .sorted(Comparator.comparingInt(Review::getRating).reversed()
                        .thenComparing(Review::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
