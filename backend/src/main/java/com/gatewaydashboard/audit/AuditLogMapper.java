package com.gatewaydashboard.audit;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;

/**
 * audit_log 数据访问（全部 SQL 见 resources/mapper/AuditLogMapper.xml）。
 */
@Mapper
public interface AuditLogMapper {

    int insert(AuditLog log);

    IPage<AuditLog> selectPageOrdered(IPage<AuditLog> page);
}
