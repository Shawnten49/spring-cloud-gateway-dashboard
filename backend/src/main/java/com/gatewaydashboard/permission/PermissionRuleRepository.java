package com.gatewaydashboard.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PermissionRuleRepository extends JpaRepository<PermissionRule, Long> {

    List<PermissionRule> findAllByOrderByPriorityAscIdAsc();
}
