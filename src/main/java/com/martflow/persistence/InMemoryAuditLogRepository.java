package com.martflow.persistence;

import com.martflow.audit.AuditLog;

/** In-memory fallback for the audit trail (hermetic tests, zero-config runs). */
public class InMemoryAuditLogRepository extends InMemoryRepository<AuditLog> {

    public InMemoryAuditLogRepository() {
        super(AuditLog::getId);
    }
}
