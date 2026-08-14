package com.gatewaydashboard.route;

import com.gatewaydashboard.route.RouteDto.RouteRequest;
import com.gatewaydashboard.route.RouteDto.Step;
import com.gatewaydashboard.route.RouteDto.ValidationResponse;
import org.springframework.cloud.gateway.filter.factory.GatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class RouteValidator {

    private static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https", "ws", "wss", "lb");

    private final Map<String, RoutePredicateFactory<?>> predicateFactories;
    private final Map<String, GatewayFilterFactory<?>> filterFactories;
    private final ConfigurationService configurationService;

    public RouteValidator(ApplicationContext context, ConfigurationService configurationService) {
        this.predicateFactories = new LinkedHashMap<>();
        context.getBeansOfType(RoutePredicateFactory.class)
                .forEach((beanName, factory) -> predicateFactories.put(factory.name(), factory));
        this.filterFactories = new LinkedHashMap<>();
        context.getBeansOfType(GatewayFilterFactory.class)
                .forEach((beanName, factory) -> filterFactories.put(factory.name(), factory));
        this.configurationService = configurationService;
    }

    public Set<String> predicateFactoryNames() {
        return new TreeSet<>(predicateFactories.keySet());
    }

    public Set<String> filterFactoryNames() {
        return new TreeSet<>(filterFactories.keySet());
    }

    public ValidationResponse validate(RouteRequest request) {
        List<String> errors = new ArrayList<>();

        if (request.routeId() == null || request.routeId().isBlank()) {
            errors.add("路由 ID 不能为空");
        } else if (!request.routeId().matches(RouteDto.ROUTE_ID_PATTERN)) {
            errors.add("路由 ID 只能包含字母、数字、点、下划线、连字符");
        }

        if (request.uri() == null || request.uri().isBlank()) {
            errors.add("目标地址不能为空");
        } else {
            try {
                URI uri = URI.create(request.uri());
                String scheme = uri.getScheme();
                if (scheme == null || !SUPPORTED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
                    errors.add("目标地址协议仅支持 http/https/ws/wss/lb");
                }
            } catch (IllegalArgumentException e) {
                errors.add("目标地址不是合法 URI");
            }
        }

        boolean enabled = request.enabled() == null || request.enabled();
        List<Step> predicates = request.predicates() == null ? List.of() : request.predicates();
        if (enabled && predicates.isEmpty()) {
            errors.add("启用状态的路由至少需要一个 predicate");
        }
        validateSteps(predicates, predicateFactories, "predicate", errors,
                (factory, name, args) -> configurationService.with((RoutePredicateFactory<?>) factory)
                        .name(name).properties(args).bind());
        validateSteps(request.filters() == null ? List.of() : request.filters(), filterFactories, "filter", errors,
                (factory, name, args) -> configurationService.with((GatewayFilterFactory<?>) factory)
                        .name(name).properties(args).bind());

        return new ValidationResponse(errors.isEmpty(), errors);
    }

    private interface StepBinder {
        void bind(Object factory, String name, Map<String, String> args);
    }

    private void validateSteps(List<Step> steps, Map<String, ?> factories, String kind,
                               List<String> errors, StepBinder binder) {
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.name() == null || step.name().isBlank()) {
                errors.add(kind + "[" + i + "] 缺少 name");
                continue;
            }
            Object factory = factories.get(step.name());
            if (factory == null) {
                errors.add(kind + "[" + i + "] 未知的工厂名: " + step.name());
                continue;
            }
            Map<String, String> args = new LinkedHashMap<>();
            if (step.args() != null) {
                int index = i;
                step.args().forEach((key, value) -> {
                    if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                        errors.add(kind + "[" + index + "] 参数 " + key + " 必须是字符串、数字或布尔值");
                    }
                    args.put(key, value == null ? "" : String.valueOf(value));
                });
            }
            try {
                binder.bind(factory, step.name(), args);
            } catch (Exception e) {
                errors.add(kind + "[" + i + "] 参数不合法: " + rootMessage(e));
            }
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? throwable.getMessage() : message;
    }
}
