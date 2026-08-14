package com.gatewaydashboard.route;

import com.gatewaydashboard.route.entity.RouteConfig;
import com.gatewaydashboard.route.mapper.RouteConfigMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关路由加载韧性回归测试（五轴审查-正确性 O1）：
 * 单行配置损坏（非法 JSON）只跳过该行并记录 ERROR，其余生效路由照常加载——
 * 不能让一条坏数据冻结全部路由（刷新失败 → 状态页 500、流量路由停滞）。
 */
@SpringBootTest
@ActiveProfiles("test")
class DbRouteDefinitionLocatorTest {

    @Autowired
    private DbRouteDefinitionLocator locator;

    @Autowired
    private RouteConfigMapper routeConfigMapper;

    @Test
    void corruptRouteRowIsSkippedButValidRoutesStillLoad() {
        String validId = "locator-valid-" + System.nanoTime();
        String corruptId = "locator-corrupt-" + System.nanoTime();

        RouteConfig valid = new RouteConfig();
        valid.setRouteId(validId);
        valid.setUri("http://httpbin.org");
        valid.setOrderNo(1);
        valid.setEnabled(true);
        valid.setPredicatesJson("[{\"name\":\"Path\",\"args\":{\"patterns\":\"/locator-valid/**\"}}]");
        valid.setFiltersJson("[]");
        valid.setMetadataJson("{}");

        RouteConfig corrupt = new RouteConfig();
        corrupt.setRouteId(corruptId);
        corrupt.setUri("http://httpbin.org");
        corrupt.setOrderNo(2);
        corrupt.setEnabled(true);
        corrupt.setPredicatesJson("this-is-not-valid-json{");
        corrupt.setFiltersJson("[]");
        corrupt.setMetadataJson("{}");

        routeConfigMapper.insert(valid);
        routeConfigMapper.insert(corrupt);
        try {
            List<RouteDefinition> definitions = Flux.from(locator.getRouteDefinitions())
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertTrue(definitions != null && definitions.stream().anyMatch(d -> d.getId().equals(validId)),
                    "合法路由应正常加载");
            assertTrue(definitions != null && definitions.stream().noneMatch(d -> d.getId().equals(corruptId)),
                    "损坏行应被跳过，而不是让整个 Flux 失败");
        } finally {
            routeConfigMapper.deleteById(valid.getId());
            routeConfigMapper.deleteById(corrupt.getId());
        }
    }
}
