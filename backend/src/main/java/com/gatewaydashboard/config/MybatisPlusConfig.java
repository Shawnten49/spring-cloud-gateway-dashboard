package com.gatewaydashboard.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.gatewaydashboard.audit.AuditLog;
import com.gatewaydashboard.auth.User;
import com.gatewaydashboard.permission.PermissionRule;
import com.gatewaydashboard.route.ConfigRevision;
import com.gatewaydashboard.route.RouteConfig;
import jakarta.annotation.PostConstruct;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 * 分页：默认 DbType.MYSQL（H2 test profile 为 MODE=MySQL，LIMIT/OFFSET 语法兼容，实测验证过）。
 * 乐观锁不依赖插件：route_config 版本冲突在 XML 中手写 WHERE version=#{version} 处理（见方案 D1）。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 预初始化实体 TableInfo：本项目 Mapper 不继承 BaseMapper（全部 SQL 走 XML，见方案 D1），
     * MP 不会在 BaseMapper 方法调用时自动构建 TableInfo 缓存；而 MetaObjectHandler 的
     * strictInsertFill 依赖 TableInfo 判断 fill 字段，缺失会导致时间戳填充失效/异常。
     */
    @PostConstruct
    public void initTableInfo() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, RouteConfig.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, AuditLog.class);
        TableInfoHelper.initTableInfo(assistant, PermissionRule.class);
        TableInfoHelper.initTableInfo(assistant, ConfigRevision.class);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
