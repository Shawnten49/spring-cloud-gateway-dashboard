package com.gatewaydashboard.route.entity;

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
 * 路由配置实体（MyBatis-Plus；全部 SQL 见 resources/mapper/RouteConfigMapper.xml）。
 * version 为乐观锁字段：updateById XML 中手写 WHERE version = #{version}，冲突返回 0 行 → 409。
 * created_at/updated_at 由 MetaObjectHandler 填充。
 */
@TableName("route_config")
@Getter
@Setter
@NoArgsConstructor
public class RouteConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("route_id")
    private String routeId;

    private String uri;

    @TableField("order_no")
    private int orderNo;

    private boolean enabled = true;

    @TableField("predicates_json")
    private String predicatesJson;

    @TableField("filters_json")
    private String filtersJson;

    @TableField("metadata_json")
    private String metadataJson;

    /** 乐观锁版本号（XML 手写 WHERE version=#{version}，非 MP @Version 插件）。 */
    private long version;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
