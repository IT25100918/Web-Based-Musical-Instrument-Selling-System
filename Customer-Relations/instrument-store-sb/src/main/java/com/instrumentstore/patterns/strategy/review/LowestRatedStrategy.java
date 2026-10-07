package com.instrumentstore.patterns.strategy.review;

import com.instrumentstore.model.Review;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** ConcreteStrategy: worst ratings first (ties: newest first) - useful for spotting problems. */
@Component
public class LowestRatedStrategy implements ReviewSortStrategy {

    @Override
    public String getKey() { return "lowest"; }

    @Override
    public String getLabel() { return "Lowest rating"; }

    @Override
    public List<Review> sort(List<Review> reviews) {
        return reviews.stream()
                .sorted(Comparator.comparingInt(Review::getRating)
                        .thenComparing(Review::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
