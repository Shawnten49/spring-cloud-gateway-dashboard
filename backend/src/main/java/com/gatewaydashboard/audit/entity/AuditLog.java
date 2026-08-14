package com.gatewaydashboard.audit.entity;

import com.gatewaydashboard.audit.AuditAction;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 操作审计实体（MyBatis-Plus；全部 SQL 见 resources/mapper/AuditLogMapper.xml）。
 * action 为 AuditAction 枚举，按 name 存库（全局 EnumTypeHandler，存量数据无需迁移）。
 * created_at 由 MetaObjectHandler 填充。
 */
@TableName("audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    /** before_json / after_json 列宽，也是快照入库前的截断上限（单一事实源，见 AuditService.truncate）。 */
    public static final int JSON_COLUMN_LENGTH = 5000;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("actor_username")
    private String actorUsername;

    private AuditAction action;

    @TableField("route_id")
    private String routeId;

    @TableField("before_json")
    private String beforeJson;

    @TableField("after_json")
    private String afterJson;

    private String ip;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
