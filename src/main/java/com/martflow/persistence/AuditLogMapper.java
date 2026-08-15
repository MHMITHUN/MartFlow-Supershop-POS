package com.martflow.persistence;

import com.martflow.audit.AuditLog;
import org.bson.Document;

import java.time.LocalDateTime;

/** Maps {@link AuditLog} documents (timestamps as ISO strings). */
public class AuditLogMapper implements DocumentMapper<AuditLog> {

    @Override
    public Document toDocument(AuditLog e) {
        return new Document("_id", e.getId())
                .append("at", e.getAt() == null ? null : e.getAt().toString())
                .append("actor", e.getActorUsername())
                .append("role", e.getRole())
                .append("action", e.getAction().name())
                .append("targetType", e.getTargetType())
                .append("targetId", e.getTargetId())
                .append("detail", e.getDetail());
    }

    @Override
    public AuditLog fromDocument(Document d) {
        return new AuditLog(
                d.getString("_id"),
                parse(d.getString("at")),
                d.getString("actor"),
                d.getString("role"),
                AuditLog.Action.valueOf(d.getString("action")),
                d.getString("targetType"),
                d.getString("targetId"),
                d.getString("detail"));
    }

    @Override
    public String idOf(AuditLog entity) {
        return entity.getId();
    }

    private static LocalDateTime parse(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw);
        } catch (Exception unparseable) {
            return null;
        }
    }
}
