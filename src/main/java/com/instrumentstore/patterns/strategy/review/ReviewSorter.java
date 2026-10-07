package com.instrumentstore.patterns.strategy.review;

import com.instrumentstore.model.Review;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategy pattern - Context. Holds every ReviewSortStrategy Spring finds and applies the one the
 * visitor picked, so the controller has no if/else on the sort name. Unknown values fall back to
 * newest-first. Adding a new sort order = adding one new strategy class.
 */
@Component
public class ReviewSorter {

    private static final String DEFAULT_KEY = "newest";

    private final Map<String, ReviewSortStrategy> strategies = new LinkedHashMap<>();

    public ReviewSorter(List<ReviewSortStrategy> all) {
        // Newest first stays first in the drop-down; the rest follow in a fixed order
        all.stream()
                .sorted(java.util.Comparator.comparing((ReviewSortStrategy s) -> !DEFAULT_KEY.equals(s.getKey()))
                        .thenComparing(ReviewSortStrategy::getKey))
                .forEach(s -> strategies.put(s.getKey(), s));
    }

    public List<Review> sort(List<Review> reviews, String key) {
        ReviewSortStrategy strategy = strategies.getOrDefault(key == null ? DEFAULT_KEY : key, strategies.get(DEFAULT_KEY));
        return strategy.sort(reviews);
    }

    /** The key actually used (so the drop-down shows the right option even for a bad ?sort= value). */
    public String resolveKey(String key) {
        return strategies.containsKey(key) ? key : DEFAULT_KEY;
    }

    public java.util.Collection<ReviewSortStrategy> getStrategies() {
        return strategies.values();
    }
}
