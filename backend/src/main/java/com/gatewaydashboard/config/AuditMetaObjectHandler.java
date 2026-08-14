package com.gatewaydashboard.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * created_at / updated_at 自动填充（替代 Hibernate @CreationTimestamp/@UpdateTimestamp）。
 * 实体 insert/update 时字段被填充，Java 侧值同步回填实体，响应（如 RouteResponse.updatedAt）可用。
 * 字段需在实体上标注 @TableField(fill = FieldFill.INSERT / INSERT_UPDATE) 才会触发填充。
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        log.debug("insertFill: {}", metaObject.getOriginalObject().getClass().getSimpleName());
        Instant now = Instant.now();
        this.strictInsertFill(metaObject, "createdAt", Instant.class, now);
        this.strictInsertFill(metaObject, "updatedAt", Instant.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        log.debug("updateFill: {}", metaObject.getOriginalObject().getClass().getSimpleName());
        this.strictUpdateFill(metaObject, "updatedAt", Instant.class, Instant.now());
    }
}
