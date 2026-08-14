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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 接口权限规则：数据库配置、按优先级匹配、修改后即时生效（无需重启）。
 * 角色语义：* = 公开；AUTHENTICATED = 任意登录用户；其余为逗号分隔的角色列表。
 *
 * 自我保护（ADR 0005）：任何增删改都必须保证 ADMIN 对权限配置模块的全部写端点
 * （POST/PUT/DELETE /api/permission-rules）以及列表 GET 依然可达，防止把自己锁死；
 * 同时 update 禁止修改内置规则，杜绝"建 VIEWER 规则 → 改内置规则提权"的绕过链（安全评审 S-13）。
 */
@Slf4j
@Service
public class PermissionRuleService {

    private static final Set<String> ALLOWED_METHODS =
            Set.of("*", "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD");
    private static final int MAX_PRIORITY = 999;

    /** 自我保护模拟的写端点：POST 建规则本身 + PUT/DELETE 操作任意规则。 */
    private static final String[] GUARD_METHODS = {"GET", "POST", "PUT", "DELETE"};
    private static final String[] GUARD_PATHS = {
            "/api/permission-rules",
            "/api/permission-rules/1",   // PUT/DELETE 的 {id} 路径
            "/api/permission-rules/__guard__"
    };

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
        if (rule.isBuiltin()) {
            // 内置规则是权限模块默认可用的底线，禁止通过 update 改写（删除已被 delete 拦截）。
            throw BusinessException.badRequest("内置规则不可修改");
        }
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
        if (normalizeRoles(request.roles()).isEmpty()) {
            throw BusinessException.badRequest("角色不能为空或全为空白");
        }
        int priority = request.priority() == null ? 0 : request.priority();
        if (priority < 0 || priority > MAX_PRIORITY) {
            throw BusinessException.badRequest("优先级必须在 0-" + MAX_PRIORITY + " 之间");
        }
    }

    /**
     * 自我保护：任何改动后，ADMIN 必须仍能访问权限配置模块的全部写端点与列表 GET，
     * 防止把自己锁死（也堵住"VIEWER 规则压过内置 → 提权改内置规则"的绕过链）。
     */
    private void guardAdminSelfAccess(List<PermissionRule> rulesAfter) {
        List<CachedRule> cached = toCached(rulesAfter);
        for (String method : GUARD_METHODS) {
            for (String path : GUARD_PATHS) {
                CachedRule matched = CachedRule.match(cached, method, path);
                if (matched == null || !matched.roles().contains("ADMIN")) {
                    throw BusinessException.badRequest(
                            "禁止保存：该改动会导致 ADMIN 失去权限配置模块 " + method + " " + path + " 的访问权限");
                }
            }
        }
    }

    private List<CachedRule> toCached(List<PermissionRule> rulesAfter) {
        return rulesAfter.stream()
                .filter(PermissionRule::isEnabled)
                .sorted(Comparator
                        .comparingInt(PermissionRule::getPriority)
                        .thenComparing(Comparator.comparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId())))
                .map(CachedRule::from)
                .toList();
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
