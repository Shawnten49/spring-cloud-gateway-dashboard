package com.gatewaydashboard.auth.mapper;

import com.gatewaydashboard.auth.entity.User;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
