package com.instrumentstore.patterns.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared bookkeeping for the concrete subjects: holds the observer list and
 * calls update() on each one. A broken observer must never break the caller
 * (e.g. a failed email must not undo a delivery status change), so each
 * update() is wrapped in try/catch.
 *
 * Note (lecture "Cons"): observers are notified in the order they were
 * registered, but none of them should rely on another having run first.
 */
public abstract class AbstractSubject<E> implements Subject<E> {

    private final List<Observer<E>> observers = new CopyOnWriteArrayList<>();

    @Override
    public void addObserver(Observer<E> observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(Observer<E> observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(E event) {
        for (Observer<E> observer : observers) {
            try {
                observer.update(event);
            } catch (RuntimeException e) {
                System.err.println("Observer " + observer.getClass().getSimpleName() + " failed: " + e.getMessage());
            }
        }
    }
}
