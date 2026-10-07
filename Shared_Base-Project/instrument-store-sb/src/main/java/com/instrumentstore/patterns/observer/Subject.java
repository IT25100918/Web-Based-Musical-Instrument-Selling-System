package com.instrumentstore.patterns.observer;

/**
 * Observer pattern (lecture "Observer" - the Subject interface).
 * Keeps a list of observers and tells all of them when something changes.
 */
public interface Subject<E> {
    void addObserver(Observer<E> observer);

    void removeObserver(Observer<E> observer);

    void notifyObservers(E event);
}
