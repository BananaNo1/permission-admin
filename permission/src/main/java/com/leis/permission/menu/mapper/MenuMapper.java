package com.leis.permission.menu.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leis.permission.menu.entity.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MenuMapper extends BaseMapper<Menu> {
    /**
     * 锁定内置系统目录行，使菜单结构维护与角色授权写入使用同一把数据库行锁。
     */
    @Select("select id from sys_menu where id = 1 for update")
    Long lockTree();
}
