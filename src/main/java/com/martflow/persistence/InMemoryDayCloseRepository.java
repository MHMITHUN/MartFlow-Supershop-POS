package com.martflow.persistence;

import com.martflow.reports.DayClose;

/** In-memory fallback for saved day closes (hermetic tests, zero-config runs). */
public class InMemoryDayCloseRepository extends InMemoryRepository<DayClose> {

    public InMemoryDayCloseRepository() {
        super(DayClose::id);
    }
}
