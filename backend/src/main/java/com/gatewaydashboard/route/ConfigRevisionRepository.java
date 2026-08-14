package com.gatewaydashboard.route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * config_revision 单行表的仓储：读取当前修订号、原子自增。
 */
public interface ConfigRevisionRepository extends JpaRepository<ConfigRevision, Integer> {

    /**
     * 原子自增（单行行锁串行化并发写），调用方须处于与路由写入相同的事务中。
     * flushAutomatically 确保路由/审计等挂起变更先落库，避免 bulk update 与实体状态乱序。
     * 自身带事务，独立调用（如测试/运维脚本）也可安全执行。
     *
     * @return 受影响行数（应为 1）
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update ConfigRevision r set r.revision = r.revision + 1 where r.id = 1")
    int bumpRevision();
}
