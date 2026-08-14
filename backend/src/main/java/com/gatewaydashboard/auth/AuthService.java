package com.gatewaydashboard.auth;

import com.gatewaydashboard.auth.AuthDtos.ChangePasswordRequest;
import com.gatewaydashboard.auth.AuthDtos.LoginRequest;
import com.gatewaydashboard.auth.AuthDtos.LoginResponse;
import com.gatewaydashboard.auth.AuthDtos.UserSummary;
import com.gatewaydashboard.common.BusinessException;
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

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> BusinessException.unauthorized("用户名或密码错误"));
        if (!user.isEnabled()) {
            throw BusinessException.unauthorized("账号已停用");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("用户名或密码错误");
        }
        // 登录成功即同步缓存（覆盖缓存加载后才创建的账号，如种子账号），保证新 token 可被过滤器校验
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
