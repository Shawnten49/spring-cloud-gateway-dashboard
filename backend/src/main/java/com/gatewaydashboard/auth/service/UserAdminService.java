package com.gatewaydashboard.auth.service;

import com.gatewaydashboard.auth.UserAuthStateCache;
import com.gatewaydashboard.auth.dto.UserAdminDtos.CreateUserRequest;
import com.gatewaydashboard.auth.dto.UserAdminDtos.UserResponse;
import com.gatewaydashboard.auth.entity.User;
import com.gatewaydashboard.auth.mapper.UserMapper;
import com.gatewaydashboard.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 用户管理（需求文档 FR1–FR5）：
 * - ADMIN 可查看/新增/屏蔽/启用用户；不允许删除（无删除接口）
 * - admin 为特殊用户，不允许被屏蔽
 * - 屏蔽/启用复用 S-04 吊销机制：token_version + 1 并同步 UserAuthStateCache，旧 token 立即失效
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    /** 特殊用户名：种子管理员，禁止被屏蔽（需求 FR4）。 */
    public static final String ADMIN_USERNAME = "admin";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthStateCache userAuthStateCache;

    @Transactional(readOnly = true)
    public List<UserResponse> list(String keyword) {
        return userMapper.selectByKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim())
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userMapper.countByUsername(request.username()) > 0) {
            throw BusinessException.conflict("用户名已存在: " + request.username());
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(true);
        user.setTokenVersion(0);
        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException e) {
            // 并发创建同名用户：count 检查与 insert 之间存在竞态窗口，唯一约束冲突应返回 409
            throw BusinessException.conflict("用户名已存在: " + request.username());
        }
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setEnabled(Long id, boolean enabled) {
        User user = find(id);
        if (ADMIN_USERNAME.equals(user.getUsername()) && !enabled) {
            throw BusinessException.badRequest("admin 为特殊用户，不允许屏蔽");
        }
        if (user.isEnabled() == enabled) {
            return UserResponse.from(user);
        }
        // 屏蔽/启用即吊销此前签发的全部 token（S-04）
        int updated = userMapper.updateEnabledWithVersion(id, enabled, Instant.now());
        if (updated == 0) {
            throw BusinessException.conflict("用户状态已被其他操作修改，请刷新后重试");
        }
        user.setEnabled(enabled);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userAuthStateCache.update(user.getUsername(), user.getTokenVersion(), enabled);
        return UserResponse.from(user);
    }

    private User find(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在: " + id);
        }
        return user;
    }
}
