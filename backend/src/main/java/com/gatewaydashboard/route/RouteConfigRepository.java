package com.gatewaydashboard.route;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RouteConfigRepository extends JpaRepository<RouteConfig, Long> {

    Optional<RouteConfig> findByRouteId(String routeId);

    boolean existsByRouteId(String routeId);

    List<RouteConfig> findAllByOrderByOrderNoAscIdAsc();

    List<RouteConfig> findAllByEnabledTrueOrderByOrderNoAscIdAsc();

    @Query("select r from RouteConfig r where lower(r.routeId) like lower(concat('%', :keyword, '%')) " +
            "or lower(r.uri) like lower(concat('%', :keyword, '%')) order by r.orderNo asc, r.id asc")
    List<RouteConfig> search(@Param("keyword") String keyword);
}
