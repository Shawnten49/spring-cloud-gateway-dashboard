package com.gatewaydashboard.route;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 路由配置全局单调修订号（单行表，id 恒为 1；MyBatis-Plus，SQL 见 ConfigRevisionMapper.xml）。
 * 每次真源写入在同一事务内 +1，作为内嵌网关轮询兜底（F5）与外部网关轮询的同一事实源。
 */
@TableName("config_revision")
@Getter
@Setter
@NoArgsConstructor
public class ConfigRevision {

    @TableId(type = IdType.INPUT)
    private int id = 1;

    @TableField("revision")
    private long revision;
}
