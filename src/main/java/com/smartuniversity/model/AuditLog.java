package com.smartuniversity.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_action_type", columnList = "action_type"),
    @Index(name = "idx_entity_type", columnList = "entity_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId; // User who performed the action

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "user_role", length = 50)
    private String userRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 100)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 100)
    private EntityType entityType;

    @Column(name = "entity_id")
    private Long entityId; // ID of the affected entity

    @Column(name = "entity_name", length = 500)
    private String entityName; // Name/title of the entity for reference

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue; // JSON or text representation of old state

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue; // JSON or text representation of new state

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ActionType {
        // Authentication actions
        LOGIN,
        LOGOUT,
        PASSWORD_RESET,
        OAUTH_LOGIN,

        // Content approval actions
        APPROVE,
        REJECT,
        PUBLISH,
        UNPUBLISH,
        HIDE,

        // CRUD actions
        CREATE,
        UPDATE,
        DELETE,
        VIEW,

        // Role and permission actions
        ROLE_CHANGE,
        PERMISSION_GRANT,
        PERMISSION_REVOKE,

        // Financial actions
        DISBURSE_FUNDS,
        RECEIVE_DONATION,
        REFUND,

        // Registration actions
        REGISTER_EVENT,
        CANCEL_REGISTRATION,
        WAITLIST_ADD,
        WAITLIST_PROMOTE,

        // Attendance actions
        MARK_ATTENDANCE,
        REMOVE_ATTENDANCE,

        // Other actions
        EXPORT_DATA,
        BULK_ACTION,
        SYSTEM_CONFIG_CHANGE
    }

    public enum EntityType {
        USER,
        EVENT,
        COMPETITION,
        ACHIEVEMENT,
        BOOK,
        LOST_FOUND_ITEM,
        FINANCIAL_AID,
        DISBURSEMENT,
        NOTIFICATION,
        EMERGENCY_NOTIFICATION,
        EVENT_REGISTRATION,
        ATTENDANCE,
        COMMENT,
        RATING,
        SYSTEM_CONFIG
    }

    // Constructors
    public AuditLog() {
    }

    public AuditLog(Long userId, String userEmail, String userRole,
                   ActionType actionType, EntityType entityType,
                   Long entityId, String entityName, String description) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.userRole = userRole;
        this.actionType = actionType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityName = entityName;
        this.description = description;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
