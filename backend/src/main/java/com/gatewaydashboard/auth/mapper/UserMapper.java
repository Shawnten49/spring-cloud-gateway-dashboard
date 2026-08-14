package com.gatewaydashboard.auth.mapper;

import com.gatewaydashboard.auth.entity.User;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * sys_user 数据访问（全部 SQL 见 resources/mapper/UserMapper.xml）。
 */
@Mapper
public interface UserMapper {

    User selectById(@Param("id") Long id);

    int insert(User user);

    int updateById(User user);

    User selectByUsername(@Param("username") String username);

    long countByUsername(@Param("username") String username);

    List<User> selectAll();

    /** 用户管理：按用户名关键词模糊搜索（可选），按 id 升序。 */
    List<User> selectByKeyword(@Param("keyword") String keyword);

    /** 用户管理：屏蔽/启用 —— enabled 切换 + token_version 自增（吊销旧 token，S-04）。 */
    int updateEnabledWithVersion(@Param("id") Long id,
                                 @Param("enabled") boolean enabled,
                                 @Param("updatedAt") Instant updatedAt);
}
