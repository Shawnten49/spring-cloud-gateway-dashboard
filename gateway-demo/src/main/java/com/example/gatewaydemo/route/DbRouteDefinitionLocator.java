package com.example.gatewaydemo.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从数据库 route_config 表加载启用中的路由，作为网关路由的唯一真源。
 * 与 Gateway Dashboard 管理后台共用同一张表、同一套配置语义。
 */
@Component
public class DbRouteDefinitionLocator implements RouteDefinitionLocator {

    private static final Logger log = LoggerFactory.getLogger(DbRouteDefinitionLocator.class);

    private static final TypeReference<List<Map<String, Object>>> STEP_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DbRouteDefinitionLocator(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<RouteDefinition> getRouteDefinitions() {
        // 阻塞 JDBC 查询卸到 boundedElastic，避免在 Netty 事件循环上执行（网关路由刷新/转发线程）。
        // 单行配置损坏（非法 JSON 等）只跳过该行并记录 ERROR：不能让一条坏数据冻结全部生效路由。
        return Mono.fromCallable(this::queryEnabledRoutes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .map(this::toDefinition)
                .onErrorContinue((error, row) ->
                        log.error("跳过无法解析的路由行 routeId={}（{}）", ((RouteConfigRow) row).routeId(), error.getMessage()));
    }

    private List<RouteConfigRow> queryEnabledRoutes() {
        return jdbcTemplate.query(
                "SELECT route_id, uri, order_no, predicates_json, filters_json, metadata_json "
                        + "FROM route_config WHERE enabled = TRUE ORDER BY order_no ASC, id ASC",
                (rs, rowNum) -> new RouteConfigRow(
                        rs.getString("route_id"),
                        rs.getString("uri"),
                        rs.getInt("order_no"),
                        rs.getString("predicates_json"),
                        rs.getString("filters_json"),
                        rs.getString("metadata_json")));
    }

    private RouteDefinition toDefinition(RouteConfigRow row) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(row.routeId());
        definition.setUri(URI.create(row.uri()));
        definition.setOrder(row.order());
        definition.setPredicates(toPredicateDefinitions(readSteps(row.predicatesJson())));
        definition.setFilters(toFilterDefinitions(readSteps(row.filtersJson())));
        definition.setMetadata(readMetadata(row.metadataJson()));
        return definition;
    }

    private List<PredicateDefinition> toPredicateDefinitions(List<Map<String, Object>> steps) {
        List<PredicateDefinition> definitions = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            PredicateDefinition definition = new PredicateDefinition();
            definition.setName(String.valueOf(step.get("name")));
            definition.setArgs(coerceArgs(asMap(step.get("args"))));
            definitions.add(definition);
        }
        return definitions;
    }

    private List<FilterDefinition> toFilterDefinitions(List<Map<String, Object>> steps) {
        List<FilterDefinition> definitions = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            FilterDefinition definition = new FilterDefinition();
            definition.setName(String.valueOf(step.get("name")));
            definition.setArgs(coerceArgs(asMap(step.get("args"))));
            definitions.add(definition);
        }
        return definitions;
    }

    private Map<String, String> coerceArgs(Map<String, Object> args) {
        Map<String, String> coerced = new LinkedHashMap<>();
        if (args != null) {
            args.forEach((key, value) -> coerced.put(key, value == null ? "" : String.valueOf(value)));
        }
        return coerced;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : new HashMap<>();
    }

    private List<Map<String, Object>> readSteps(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STEP_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("路由断言/过滤器配置解析失败: " + json, e);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("路由元数据解析失败: " + json, e);
        }
    }
}
