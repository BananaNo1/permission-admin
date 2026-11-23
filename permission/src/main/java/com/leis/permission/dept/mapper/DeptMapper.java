package com.leis.permission.dept.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.dept.entity.Dept;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeptMapper extends BaseMapper<Dept> {

    /**
     * 锁定根部门行，为组织结构和用户部门关联的写操作提供互斥入口。
     */
    @Select("select id from sys_dept where id = 1 for update")
    Long lockTree();
}
