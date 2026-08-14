package com.gatewaydashboard.auth;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户认证状态内存缓存（S-04 吊销机制的校验侧）：
 * 维护 username → (tokenVersion, enabled)，供 JWT 过滤器**无阻塞**比对，
 * 避免每个请求在 Netty 事件循环上查库。
 *
 * 填充时机：
 * 1. 启动 {@link #loadAll()} 全量加载（保证重启后已签发 token 仍有效）；
 * 2. 登录成功 / 改密后由 AuthService 调用 {@link #update} 同步最新值。
 *
 * 边界（单实例 MVP）：多实例需共享失效通道（未来与 F5 演进同步，见 docs/优化方案.md）。
 */
@Slf4j
@Component
public class UserAuthStateCache {

    private record UserState(long tokenVersion, boolean enabled) {
    }

    private final UserRepository userRepository;
    private final ConcurrentHashMap<String, UserState> states = new ConcurrentHashMap<>();

    public UserAuthStateCache(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void loadAll() {
        for (User user : userRepository.findAll()) {
            states.put(user.getUsername(), new UserState(user.getTokenVersion(), user.isEnabled()));
        }
        log.info("用户认证状态缓存已加载: {} 个用户", states.size());
    }

    /**
     * JWT 有效性校验：用户存在、未停用、token 版本与当前一致。
     * 缓存缺失（如绕过应用直连建号）一律视为无效，走 401（fail-closed）。
     */
    public boolean isTokenValid(String username, long tokenVersion) {
        UserState state = states.get(username);
        if (state == null) {
            log.debug("用户认证状态缺失（可能为绕过应用创建）: {}", username);
            return false;
        }
        return state.enabled() && state.tokenVersion() == tokenVersion;
    }

    /**
     * 用已知最新值直接更新缓存（登录成功/改密后调用）。
     * 传入的是当前事务内已确定的值，避免再次查库读到旧版本。
     */
    public void update(String username, long tokenVersion, boolean enabled) {
        states.put(username, new UserState(tokenVersion, enabled));
    }
}
