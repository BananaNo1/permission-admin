package com.leis.permission.log.mapper;

import com.leis.permission.log.dto.AuditCommand;
import com.leis.permission.log.vo.LoginLogVO;
import com.leis.permission.log.vo.OperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 写入登录与操作审计，并提供审计分页和计数查询。
 */
@Mapper
public interface AuditMapper {

    /**
     * 将操作审计命令写入操作日志表。
     */
    void operation(@Param("c") AuditCommand command);


    /**
     * 写入一次登录尝试的结果和追踪信息。
     */
    void login(@Param("username") String username, @Param("userId") Long userId, @Param("success") boolean success, @Param("ip") String ip, @Param("traceId") String traceId);

    /**
     * 按 ID 倒序分页读取登录日志。
     */
    List<LoginLogVO> logins(@Param("limit") int limit, @Param("offset") long offset);

    /**
     * 按 ID 倒序分页读取操作日志。
     */
    List<OperationLogVO> operations(@Param("limit") int limit, @Param("offset") long offset);

    /**
     * 统计登录日志总条数，供分页使用。
     */
    long loginCount();
    /**
     * 统计操作日志总条数，供分页使用。
     */
    long operationCount();
}
