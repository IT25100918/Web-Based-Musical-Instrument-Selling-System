package com.instrumentstore.patterns.strategy.review;

import com.instrumentstore.model.Review;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** ConcreteStrategy: most recently written/edited first (the original behaviour, and the default). */
@Component
public class NewestFirstStrategy implements ReviewSortStrategy {

    @Override
    public String getKey() { return "newest"; }

    @Override
    public String getLabel() { return "Newest first"; }

    @Override
    public List<Review> sort(List<Review> reviews) {
        return reviews.stream()
                .sorted(Comparator.comparing(Review::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
