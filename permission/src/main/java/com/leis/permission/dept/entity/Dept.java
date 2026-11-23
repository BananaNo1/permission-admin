package com.leis.permission.dept.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sys_dept")
public class Dept {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    private String name;
    private Integer sortOrder;
    private String status;
    @Version
    private Integer version;
}