package com.gatewaydashboard.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    /** before_json / after_json 列宽，也是快照入库前的截断上限（单一事实源，见 AuditService.truncate）。 */
    public static final int JSON_COLUMN_LENGTH = 5000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_username", nullable = false, length = 64)
    private String actorUsername;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "route_id", length = 128)
    private String routeId;

    @Column(name = "before_json", length = JSON_COLUMN_LENGTH)
    private String beforeJson;

    @Column(name = "after_json", length = JSON_COLUMN_LENGTH)
    private String afterJson;

    @Column(length = 64)
    private String ip;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
