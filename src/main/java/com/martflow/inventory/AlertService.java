package com.martflow.inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>Pattern: Observer.</b> The store's alert center: subscribes to every inventory item and
 * keeps a capped, queryable feed of events (low stock, restocks, price changes, expiry,
 * wastage). Services also raise events directly here ({@link #raise}).
 *
 * <p>The feed is deliberately ephemeral and capped — it is an operations dashboard, not an audit
 * log. Marking alerts read is per feed entry.
 */
public class AlertService implements StockObserver {

    /** One feed entry: the event plus its read flag. */
    public static final class Alert {

        private final String id;
        private final StockEvent event;
        private volatile boolean read;

        private Alert(String id, StockEvent event) {
            this.id = id;
            this.event = event;
        }

        public String getId() {
            return id;
        }

        public StockEvent getEvent() {
            return event;
        }

        public boolean isRead() {
            return read;
        }
    }

    private final int capacity;
    private final Deque<Alert> feed = new ArrayDeque<>();
    private final AtomicLong idSequence = new AtomicLong();

    public AlertService(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Alert capacity must be positive");
        }
        this.capacity = capacity;
    }

    @Override
    public synchronized void update(StockEvent event) {
        append(event);
    }

    /** Raises a service-originated event (expiry, wastage, reorder suggestions). */
    public synchronized void raise(StockEvent event) {
        append(event);
    }

    private void append(StockEvent event) {
        feed.addLast(new Alert("al-" + idSequence.incrementAndGet(), event));
        while (feed.size() > capacity) {
            feed.removeFirst();
        }
    }

    /** Newest-last snapshot of the whole feed. */
    public synchronized List<Alert> all() {
        return new ArrayList<>(feed);
    }

    /** Newest-last snapshot of only the unread entries. */
    public synchronized List<Alert> unread() {
        List<Alert> result = new ArrayList<>();
        for (Alert alert : feed) {
            if (!alert.read) {
                result.add(alert);
            }
        }
        return result;
    }

    /** Marks one alert read. Returns {@code false} for unknown ids. */
    public synchronized boolean markRead(String id) {
        for (Alert alert : feed) {
            if (alert.getId().equals(id)) {
                alert.read = true;
                return true;
            }
        }
        return false;
    }

    public synchronized void clear() {
        feed.clear();
    }
}
