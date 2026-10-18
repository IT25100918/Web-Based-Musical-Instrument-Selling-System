package com.instrumentstore.patterns.strategy.review;

import com.instrumentstore.model.Review;

import java.util.List;

/** Strategy pattern - Strategy interface: one way of ordering the reviews shown on a product page. */
public interface ReviewSortStrategy {

    /** Value used in the ?sort= URL parameter and the drop-down. */
    String getKey();

    /** Text shown in the drop-down. */
    String getLabel();

    /** Returns a NEW sorted list; the input list is not changed. */
    List<Review> sort(List<Review> reviews);
}
