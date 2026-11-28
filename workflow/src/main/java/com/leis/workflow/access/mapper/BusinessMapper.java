package com.leis.workflow.access.mapper;

import com.leis.workflow.access.BusinessAccess.Business;
import com.leis.workflow.access.BusinessAccess.Grant;
import com.leis.workflow.access.BusinessAccess.Rights;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 访问流程业务和用户授权记录，提供事务内的业务行锁。
 */
@Mapper
public interface BusinessMapper {
    /** 锁定指定业务行，业务不存在时返回空。 */
    String lockCode(String code);

    /** 判断指定业务是否存在。 */
    boolean exists(String code);

    /** 按用户和管理员身份查询业务列表及有效权限。 */
    List<Business> findVisible(long userId, boolean admin);

    /** 读取用户在指定业务上的授权，未授权时返回空。 */
    Rights findRights(String code, long userId);

    /** 按用户标识排序查询指定业务下的授权记录。 */
    List<Grant> findGrants(String code);

    /** 写入新的流程业务。 */
    void insertBusiness(String code, String name);

    /** 更新已有授权并返回受影响的行数。 */
    int updateGrant(String code, long userId, Rights rights);

    /** 仅在账号存在时新增授权并返回受影响的行数。 */
    int insertGrant(String code, long userId, Rights rights);

    /** 删除指定用户在该业务上的授权。 */
    void deleteGrant(String code, long userId);
}
