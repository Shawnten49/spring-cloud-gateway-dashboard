package com.gatewaydashboard.permission.entity;

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
 * 接口权限规则实体（MyBatis-Plus；全部 SQL 见 resources/mapper/PermissionRuleMapper.xml）。
 * created_at/updated_at 由 MetaObjectHandler 填充。
 */
@TableName("permission_rule")
@Getter
@Setter
@NoArgsConstructor
public class PermissionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("http_method")
    private String httpMethod;

    @TableField("path_pattern")
    private String pathPattern;

    private String roles;

    private int priority;

    private boolean enabled = true;

    private boolean builtin = false;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
