package com.gatewaydashboard.permission.mapper;

import com.gatewaydashboard.permission.entity.PermissionRule;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * permission_rule 数据访问（全部 SQL 见 resources/mapper/PermissionRuleMapper.xml）。
 */
@Mapper
public interface PermissionRuleMapper {

    PermissionRule selectById(@Param("id") Long id);

    int insert(PermissionRule rule);

    int updateById(PermissionRule rule);

    int deleteById(@Param("id") Long id);

    /** 按优先级、id 排序（权限缓存 reload 用）。 */
    List<PermissionRule> selectAllOrdered();

    /** 全量（守卫模拟用）。 */
    List<PermissionRule> selectAll();
}
