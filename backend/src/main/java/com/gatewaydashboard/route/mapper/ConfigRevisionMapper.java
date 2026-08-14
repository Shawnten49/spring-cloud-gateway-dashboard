package com.gatewaydashboard.route.mapper;

import com.gatewaydashboard.route.entity.ConfigRevision;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * config_revision 单行表数据访问（全部 SQL 见 resources/mapper/ConfigRevisionMapper.xml）。
 */
@Mapper
public interface ConfigRevisionMapper {

    ConfigRevision selectById(@Param("id") Integer id);

    /** 原子自增（单行行锁串行化并发写），调用方须处于与路由写入相同的事务中。 */
    int bumpRevision();
}
