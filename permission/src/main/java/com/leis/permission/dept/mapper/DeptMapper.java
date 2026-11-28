package com.leis.permission.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.dept.entity.Dept;
import org.apache.ibatis.annotations.Mapper;

/**
 * 负责部门表访问，并提供组织树写操作使用的锁定入口。
 */
@Mapper
public interface DeptMapper extends BaseMapper<Dept> {

    /**
     * 锁定根部门行，为组织结构和用户部门关联的写操作提供互斥入口。
     */
    Long lockTree();
}
