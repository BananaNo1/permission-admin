package com.leis.permission.log.mapper;

import com.leis.permission.log.dto.AuditCommand;
import com.leis.permission.log.vo.LoginLogVO;
import com.leis.permission.log.vo.OperationLogVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AuditMapper {

    /**
     * 将操作审计命令写入操作日志表。
     */
    @Insert("""
            insert into sys_operation_log(actor_id,actor_username,action,target_type,target_id,
                success,summary,trace_id,duration_ms)
            values(#{c.actorId},#{c.actorUsername},#{c.action},#{c.targetType},#{c.targetId},
                #{c.success},#{c.summary},#{c.traceId},0)
            """)
    void operation(@Param("c") AuditCommand command);


    /**
     * 写入一次登录尝试的结果和追踪信息。
     */
    @Insert("""
        insert into sys_login_log(username,user_id,success,ip,trace_id)
        values(#{username},#{userId},#{success},#{ip},#{traceId})
        """)
    void login(String username, Long userId, boolean success, String ip, String traceId);

    /**
     * 按 ID 倒序分页读取登录日志。
     */
    @Select("""
        select id::text,username,success,ip,trace_id,created_at::text
        from sys_login_log order by id desc limit #{limit} offset #{offset}
        """)
    List<LoginLogVO> logins(int limit, long offset);

    /**
     * 按 ID 倒序分页读取操作日志。
     */
    @Select("""
        select id::text,actor_username,action,target_type,target_id,success,
               summary,trace_id,created_at::text
        from sys_operation_log order by id desc limit #{limit} offset #{offset}
        """)
    List<OperationLogVO> operations(int limit, long offset);

    /**
     * 统计登录日志总条数，供分页使用。
     */
    @Select("select count(*) from sys_login_log")
    long loginCount();
    /**
     * 统计操作日志总条数，供分页使用。
     */
    @Select("select count(*) from sys_operation_log")
    long operationCount();
}
