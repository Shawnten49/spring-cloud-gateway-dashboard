package com.gatewaydashboard.route;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * route_config 数据访问（全部 SQL 见 resources/mapper/RouteConfigMapper.xml，便于人工维护）。
 */
@Mapper
public interface RouteConfigMapper {

    RouteConfig selectById(@Param("id") Long id);

    int insert(RouteConfig route);

    /** 乐观锁更新：WHERE version = #{version}，受影响行数 0 表示版本冲突（Service 层映射 409）。 */
    int updateByIdWithVersion(RouteConfig route);

    int deleteById(@Param("id") Long id);

    RouteConfig selectByRouteId(@Param("routeId") String routeId);

    long countByRouteId(@Param("routeId") String routeId);

    List<RouteConfig> selectAllOrdered();

    List<RouteConfig> selectEnabledOrdered();

    List<RouteConfig> searchByKeyword(@Param("keyword") String keyword);

    long countAll();
}
