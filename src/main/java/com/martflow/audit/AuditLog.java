package com.martflow.audit;

import java.time.LocalDateTime;

/**
 * One append-only activity entry: who did what, to what, and when. This is the trail the owner
 * asks for after a bad week — "ke void dilam, ke price change korlo, ke points adjust korlo".
 */
public class AuditLog {

    /** The audited action catalog — one entry per business operation at the API boundary. */
    public enum Action {
        LOGIN, LOGIN_FAILED, LOGOUT,
        SALE_VOIDED, RETURN_PROCESSED,
        PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED,
        STOCK_RESTOCKED, SHRINKAGE_RECORDED,
        POINTS_ADJUSTED,
        PROMOTION_CREATED, PROMOTION_UPDATED, PROMOTION_DELETED,
        PO_SUBMITTED, PO_CANCELLED, PO_RECEIVED, PO_PAID, PO_CLOSED,
        TEMPLATE_SAVED, TEMPLATE_CLONED,
        USER_CREATED, USER_UPDATED,
        DAY_CLOSED
    }

    private final String id;
    private final LocalDateTime at;
    private final String actorUsername;
    private final String role;
    private final Action action;
    private final String targetType;
    private final String targetId;
    private final String detail;

    public AuditLog(String id, LocalDateTime at, String actorUsername, String role,
                    Action action, String targetType, String targetId, String detail) {
        this.id = id;
        this.at = at;
        this.actorUsername = actorUsername == null ? "-" : actorUsername;
        this.role = role == null ? "-" : role;
        this.action = action;
        this.targetType = targetType == null ? "" : targetType;
        this.targetId = targetId == null ? "" : targetId;
        this.detail = detail == null ? "" : detail;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getRole() {
        return role;
    }

    public Action getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return "[" + at + "] " + actorUsername + "/" + role + " " + action + " " + targetId;
    }
}
