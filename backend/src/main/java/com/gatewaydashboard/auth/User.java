package com.gatewaydashboard.auth;

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
 * 用户实体（MyBatis-Plus；全部 SQL 见 resources/mapper/UserMapper.xml）。
 * tokenVersion 为 S-04 吊销机制的版本号；created_at/updated_at 由 MetaObjectHandler 填充。
 */
@TableName("sys_user")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @TableField("password_hash")
    private String passwordHash;

    private String role;

    private boolean enabled = true;

    /**
     * 用户级 token 版本号（S-04 吊销机制）：改密/停用时 +1，
     * JWT 携带 ver claim，过滤器比对不一致即视为已吊销。
     */
    @TableField("token_version")
    private long tokenVersion;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
