package com.gatewaydashboard.permission;

import com.gatewaydashboard.common.BusinessException;
import com.gatewaydashboard.permission.PermissionRuleDtos.RuleRequest;
import com.gatewaydashboard.permission.PermissionRuleDtos.RuleResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 接口权限规则：数据库配置、按优先级匹配、修改后即时生效（无需重启）。
 * 角色语义：* = 公开；AUTHENTICATED = 任意登录用户；其余为逗号分隔的角色列表。
 */
@Slf4j
@Service
public class PermissionRuleService {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("*", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
    private static final String SELF_GUARD_PATH = "/api/permission-rules/__guard__";

    private final PermissionRuleRepository repository;
    private final AtomicReference<List<CachedRule>> rules = new AtomicReference<>(List.of());

    public PermissionRuleService(PermissionRuleRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        return repository.findAllByOrderByPriorityAscIdAsc().stream()
                .map(RuleResponse::from)
                .toList();
    }

    @Transactional
    public RuleResponse create(RuleRequest request) {
        validateRequest(request);
        PermissionRule rule = new PermissionRule();
        apply(rule, request);
        rule.setBuiltin(false);
        List<PermissionRule> rulesAfter = new ArrayList<>(repository.findAll());
        rulesAfter.add(rule);
        guardAdminSelfAccess(rulesAfter);
        PermissionRule saved = repository.save(rule);
        reload();
        log.info("新增权限规则: {} {} {} -> {}", saved.getHttpMethod(), saved.getPathPattern(), saved.getRoles(), saved.getPriority());
        return RuleResponse.from(saved);
    }

    @Transactional
    public RuleResponse update(Long id, RuleRequest request) {
        PermissionRule rule = find(id);
        validateRequest(request);
        apply(rule, request);
        List<PermissionRule> rulesAfter = new ArrayList<>(repository.findAll());
        rulesAfter.replaceAll(r -> r.getId().equals(id) ? rule : r);
        guardAdminSelfAccess(rulesAfter);
        PermissionRule saved = repository.saveAndFlush(rule);
        reload();
        log.info("更新权限规则 #{}: {} {} {} -> {}", saved.getId(), saved.getHttpMethod(), saved.getPathPattern(), saved.getRoles(), saved.getPriority());
        return RuleResponse.from(saved);
    }

    @Transactional
    public void delete(Long id) {
        PermissionRule rule = find(id);
        if (rule.isBuiltin()) {
            throw BusinessException.badRequest("内置规则不可删除");
        }
        List<PermissionRule> rulesAfter = new ArrayList<>(repository.findAll());
        rulesAfter.removeIf(r -> r.getId().equals(id));
        guardAdminSelfAccess(rulesAfter);
        repository.delete(rule);
        reload();
        log.info("删除权限规则 #{}: {} {}", id, rule.getHttpMethod(), rule.getPathPattern());
    }

    /**
     * 重新从数据库加载规则（启动、以及每次增删改后调用），使改动即时生效。
     */
    public void reload() {
        List<CachedRule> cached = repository.findAllByOrderByPriorityAscIdAsc().stream()
                .filter(PermissionRule::isEnabled)
                .map(CachedRule::from)
                .toList();
        rules.set(cached);
    }

    /**
     * 按优先级找到第一条匹配的启用规则；无匹配返回 null（默认拒绝）。
     */
    public CachedRule match(String httpMethod, String path) {
        String method = httpMethod == null ? "" : httpMethod.toUpperCase(Locale.ROOT);
        for (CachedRule rule : rules.get()) {
            if (rule.matches(method, path)) {
                return rule;
            }
        }
        return null;
    }

    public boolean isAllowed(CachedRule rule, Set<String> authorities) {
        if (rule.roles().contains("*")) {
            return true;
        }
        if (rule.roles().contains("AUTHENTICATED")) {
            return true;
        }
        return rule.roles().stream().anyMatch(authorities::contains);
    }

    private void validateRequest(RuleRequest request) {
        String method = request.httpMethod().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method)) {
            throw BusinessException.badRequest("方法仅支持 GET/POST/PUT/DELETE/PATCH/OPTIONS/HEAD/*");
        }
        if (!request.pathPattern().trim().startsWith("/")) {
            throw BusinessException.badRequest("路径必须以 / 开头，如 /api/routes/**");
        }
        try {
            PathPatternParser.defaultInstance.parse(request.pathPattern().trim());
        } catch (Exception e) {
            throw BusinessException.badRequest("路径模式不合法: " + e.getMessage());
        }
    }

    /**
     * 自我保护：任何改动后，ADMIN 必须仍能访问权限配置模块的写接口，防止把自己锁死。
     */
    private void guardAdminSelfAccess(List<PermissionRule> rulesAfter) {
        List<CachedRule> cached = rulesAfter.stream()
                .filter(PermissionRule::isEnabled)
                .sorted((a, b) -> a.getPriority() != b.getPriority()
                        ? Integer.compare(a.getPriority(), b.getPriority())
                        : Long.compare(a.getId() == null ? Long.MAX_VALUE : a.getId(),
                        b.getId() == null ? Long.MAX_VALUE : b.getId()))
                .map(CachedRule::from)
                .toList();
        CachedRule matched = CachedRule.match(cached, "POST", SELF_GUARD_PATH);
        if (matched == null || !matched.roles().contains("ADMIN")) {
            throw BusinessException.badRequest("禁止保存：该改动会导致 ADMIN 失去权限配置模块的访问权限");
        }
    }

    private PermissionRule find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("权限规则不存在: " + id));
    }

    private void apply(PermissionRule rule, RuleRequest request) {
        rule.setName(request.name().trim());
        rule.setHttpMethod(request.httpMethod().trim().toUpperCase(Locale.ROOT));
        rule.setPathPattern(request.pathPattern().trim());
        rule.setRoles(normalizeRoles(request.roles()));
        rule.setPriority(request.priority() == null ? 0 : request.priority());
        rule.setEnabled(request.enabled() == null || request.enabled());
    }

    private String normalizeRoles(String roles) {
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.equalsIgnoreCase("authenticated") ? "AUTHENTICATED" : s.toUpperCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 内存中的规则快照（已编译路径模式，避免每次请求重复解析）。
     */
    public record CachedRule(Long id, String httpMethod, String pathPattern, Set<String> roles, int priority,
                             PathPattern pattern) {

        static CachedRule from(PermissionRule rule) {
            return new CachedRule(
                    rule.getId(),
                    rule.getHttpMethod().toUpperCase(Locale.ROOT),
                    rule.getPathPattern(),
                    new HashSet<>(Arrays.asList(rule.getRoles().split(","))),
                    rule.getPriority(),
                    PathPatternParser.defaultInstance.parse(rule.getPathPattern()));
        }

        boolean matches(String method, String path) {
            if (!httpMethod.equals("*") && !httpMethod.equals(method)) {
                return false;
            }
            return pattern.matches(PathContainer.parsePath(path));
        }

        static CachedRule match(List<CachedRule> rules, String method, String path) {
            for (CachedRule rule : rules) {
                if (rule.matches(method, path)) {
                    return rule;
                }
            }
            return null;
        }
    }
}
