package com.gatewaydashboard.auth;

import com.gatewaydashboard.auth.AuthDtos.ChangePasswordRequest;
import com.gatewaydashboard.auth.AuthDtos.LoginRequest;
import com.gatewaydashboard.auth.AuthDtos.LoginResponse;
import com.gatewaydashboard.auth.AuthDtos.UserSummary;
import com.gatewaydashboard.common.BusinessException;
import com.gatewaydashboard.config.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserAuthStateCache userAuthStateCache;
    private final LoginRateLimiter loginRateLimiter;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request, String clientIp) {
        // 登录限流（按客户端 IP 令牌桶）：超限直接 429，不再消耗 BCrypt 算力
        if (!loginRateLimiter.tryConsume(clientIp == null ? "unknown" : clientIp)) {
            throw BusinessException.tooManyRequests("登录尝试过于频繁，请稍后再试");
        }
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!user.isEnabled()) {
            throw BusinessException.unauthorized("账号已停用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        // 登录成功：重置该 IP 的限流计数，避免误伤正常用户；并同步认证状态缓存
        loginRateLimiter.reset(clientIp == null ? "unknown" : clientIp);
        userAuthStateCache.update(user.getUsername(), user.getTokenVersion(), user.isEnabled());
        return new LoginResponse(jwtService.generate(user), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public UserSummary me(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.unauthorized("用户不存在"));
        return UserSummary.from(user);
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("原密码错误");
        }
        // S-04 吊销：改密即吊销该用户此前签发的全部 token（JWT ver 不再匹配）
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        userAuthStateCache.update(user.getUsername(), user.getTokenVersion(), user.isEnabled());
    }
}
