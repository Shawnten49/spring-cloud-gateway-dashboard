package com.gatewaydashboard.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatewaydashboard.route.RouteDto.RouteRequest;
import com.gatewaydashboard.route.RouteDto.RouteResponse;
import com.gatewaydashboard.route.RouteDto.Step;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RouteAssembler {

    private static final TypeReference<List<Step>> STEP_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public RouteAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RouteConfig toEntity(RouteRequest request) {
        RouteConfig entity = new RouteConfig();
        entity.setRouteId(request.routeId());
        applyRequest(entity, request);
        return entity;
    }

    public void applyRequest(RouteConfig entity, RouteRequest request) {
        entity.setUri(request.uri());
        entity.setOrderNo(request.order() == null ? 0 : request.order());
        entity.setEnabled(request.enabled() == null || request.enabled());
        entity.setPredicatesJson(writeList(request.predicates()));
        entity.setFiltersJson(writeList(request.filters()));
        entity.setMetadataJson(writeMap(request.metadata()));
    }

    public RouteResponse toResponse(RouteConfig entity) {
        return new RouteResponse(
                entity.getRouteId(),
                entity.getUri(),
                entity.getOrderNo(),
                entity.isEnabled(),
                readSteps(entity.getPredicatesJson()),
                readSteps(entity.getFiltersJson()),
                readMetadata(entity.getMetadataJson()),
                entity.getVersion(),
                entity.getUpdatedAt());
    }

    public RouteResponse toResponse(RouteDefinition definition) {
        return new RouteResponse(
                definition.getId(),
                definition.getUri() == null ? "" : definition.getUri().toString(),
                definition.getOrder(),
                true,
                toSteps(definition.getPredicates()),
                toSteps(definition.getFilters()),
                definition.getMetadata(),
                0,
                null);
    }

    /**
     * 网关"生效路由"视图：来自 CachingRouteLocator 的 Route（已在内存中生效），
     * 只展示路由级信息；predicate/filter 细节在运行态不可还原为结构化 Step，置空。
     */
    public RouteResponse toResponse(Route route) {
        return toResponse(route, null);
    }

    /**
     * 网关"生效路由"视图（含结构化断言/过滤器）：
     * 生效集合取自 CachingRouteLocator 的 Route，predicates/filters 内容按 routeId
     * 从路由定义（真源）匹配填充——运行态 Route 只保留解析后的谓词闭包，无法还原结构化 Step。
     * definition 缺失（防御：理论不可达）时 predicates/filters 置空。
     */
    public RouteResponse toResponse(Route route, RouteDefinition definition) {
        return new RouteResponse(
                route.getId(),
                route.getUri() == null ? "" : route.getUri().toString(),
                route.getOrder(),
                true,
                definition == null ? List.of() : toSteps(definition.getPredicates()),
                definition == null ? List.of() : toSteps(definition.getFilters()),
                definition == null ? route.getMetadata() : definition.getMetadata(),
                0,
                null);
    }

    public RouteDefinition toDefinition(RouteConfig entity) {
        RouteDefinition definition = new RouteDefinition();
        definition.setId(entity.getRouteId());
        definition.setUri(URI.create(entity.getUri()));
        definition.setOrder(entity.getOrderNo());
        definition.setPredicates(toPredicateDefinitions(readSteps(entity.getPredicatesJson())));
        definition.setFilters(toFilterDefinitions(readSteps(entity.getFiltersJson())));
        definition.setMetadata(readMetadata(entity.getMetadataJson()));
        return definition;
    }

    public RouteRequest toRequest(RouteConfig entity) {
        return new RouteRequest(
                entity.getRouteId(),
                entity.getUri(),
                entity.getOrderNo(),
                entity.isEnabled(),
                readSteps(entity.getPredicatesJson()),
                readSteps(entity.getFiltersJson()),
                readMetadata(entity.getMetadataJson()));
    }

    public String toJson(RouteConfig entity) {
        try {
            return objectMapper.writeValueAsString(toResponse(entity));
        } catch (Exception e) {
            // 审计是"保存即生效"（ADR 0003）唯一的追溯手段：序列化失败绝不能静默丢失，
            // 记录日志并写入显式占位串，保证审计行内容可被发现而非静默为 null。
            log.error("路由快照序列化失败（routeId={}），审计内容将写入占位串", entity.getRouteId(), e);
            return "{\"error\":\"snapshot-serialization-failed\",\"routeId\":\"" + entity.getRouteId() + "\"}";
        }
    }

    private List<PredicateDefinition> toPredicateDefinitions(List<Step> steps) {
        List<PredicateDefinition> definitions = new ArrayList<>();
        for (Step step : steps) {
            PredicateDefinition definition = new PredicateDefinition();
            definition.setName(step.name());
            definition.setArgs(coerceArgs(step.args()));
            definitions.add(definition);
        }
        return definitions;
    }

    private List<FilterDefinition> toFilterDefinitions(List<Step> steps) {
        List<FilterDefinition> definitions = new ArrayList<>();
        for (Step step : steps) {
            FilterDefinition definition = new FilterDefinition();
            definition.setName(step.name());
            definition.setArgs(coerceArgs(step.args()));
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

    private List<Step> toSteps(List<?> definitions) {
        List<Step> steps = new ArrayList<>();
        if (definitions != null) {
            for (Object definition : definitions) {
                if (definition instanceof PredicateDefinition pd) {
                    steps.add(new Step(pd.getName(), new HashMap<>(pd.getArgs())));
                } else if (definition instanceof FilterDefinition fd) {
                    steps.add(new Step(fd.getName(), new HashMap<>(fd.getArgs())));
                }
            }
        }
        return steps;
    }

    private List<Step> readSteps(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, STEP_LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("路由断言/过滤器配置解析失败", e);
        }
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("路由元数据解析失败", e);
        }
    }

    private String writeList(List<Step> steps) {
        try {
            return objectMapper.writeValueAsString(steps == null ? List.of() : steps);
        } catch (Exception e) {
            throw new IllegalStateException("路由断言/过滤器序列化失败", e);
        }
    }

    private String writeMap(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception e) {
            throw new IllegalStateException("路由元数据序列化失败", e);
        }
    }
}
