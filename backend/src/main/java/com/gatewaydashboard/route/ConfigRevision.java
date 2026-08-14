package com.gatewaydashboard.route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 路由配置全局单调修订号（单行表，id 恒为 1）。
 * 每次真源写入（RouteService create/update/delete/setEnabled、种子初始化）在同一事务内 +1，
 * 作为内嵌网关轮询兜底（F5）与外部网关轮询（gateway-demo）的同一事实源，
 * 替代 (COUNT, SUM(version)) 校验和，消除理论碰撞漏检（评审 F13）。
 */
@Entity
@Table(name = "config_revision")
@Getter
@Setter
@NoArgsConstructor
public class ConfigRevision {

    @Id
    @Column(nullable = false)
    private int id = 1;

    @Column(nullable = false)
    private long revision;
}
