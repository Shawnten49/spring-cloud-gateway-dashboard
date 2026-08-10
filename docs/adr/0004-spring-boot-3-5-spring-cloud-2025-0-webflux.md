# v1 采用 Spring Boot 3.5.x + Spring Cloud 2025.0.x（Gateway 4.3.x，WebFlux）

后端版本线选择 Spring Boot 3.5.x + Spring Cloud 2025.0.x（Northfields），Gateway 使用经典 WebFlux 变体（`spring-cloud-starter-gateway-server-webflux`），而不是 2026 年当前主线的 Spring Boot 4.x + Spring Cloud 2025.1.x（Oakwood）或新增的 Gateway WebMVC 变体。

理由：Boot 3.5 + Cloud 2025.0 是资料最全、教程最多、生态最成熟的组合，v1 目标是尽快跑通核心链路；Boot 3.5 的 OSS 支持期已于 2026-06 结束，但对学习和演示项目无实质影响。Boot 4 / Oakwood / Gateway WebMVC 变体较新，待生态成熟后再评估迁移。

Status: accepted
