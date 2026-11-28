package com.leis.permission.menu.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.menu.entity.Menu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供菜单表的持久化操作与菜单树写入时的锁定查询。
 */
@Mapper
public interface MenuMapper extends BaseMapper<Menu> {
    /**
     * 锁定内置系统目录行，使菜单结构维护与角色授权写入使用同一把数据库行锁。
     */
    Long lockTree();
}
