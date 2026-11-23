alter table sys_menu add column description varchar(300);

update sys_menu as menu
set description = seed.description,
    name = case
        when seed.display_name is not null
            and menu.name = regexp_replace(seed.permission_code, '^.*:', '')
            then seed.display_name
        else menu.name
    end
from (values
    ('system:user:list', null, '进入用户管理，搜索和查看数据范围内的用户列表。'),
    ('system:user:query', '查看用户详情', '查看单个用户的账号、姓名、部门和状态。'),
    ('system:user:create', '新增用户', '在允许管理的部门下创建账号并设置初始密码。'),
    ('system:user:update', '编辑和启停用户', '修改用户姓名或所属部门，也可以启用、禁用账号。'),
    ('system:user:reset-password', '重置用户密码', '为指定用户设置新密码，并使其旧会话失效。'),
    ('system:user:delete', '删除用户', '删除数据范围内的用户账号。'),
    ('system:role:list', null, '进入角色管理，查看角色、状态和数据范围。 当前仅超级管理员可执行。'),
    ('system:role:query', '查询角色', '预留标识。角色列表当前由管理员权限控制。'),
    ('system:role:create', '新增角色', '创建角色并设置其数据范围。 当前仅超级管理员可执行。'),
    ('system:role:update', '编辑和启停角色', '修改角色名称、数据范围或启用状态。 当前仅超级管理员可执行。'),
    ('system:role:grant', '为角色分配权限', '配置该角色可以访问的菜单和执行的操作。 当前仅超级管理员可执行。'),
    ('system:role:delete', '删除角色', '删除符合条件的角色。 当前仅超级管理员可执行。'),
    ('system:dept:list', null, '查看组织树。管理页面仅超级管理员可进入，部门列表接口按数据范围返回。'),
    ('system:dept:query', '查询部门', '预留标识。部门列表当前按数据范围返回。'),
    ('system:dept:create', '新增部门', '创建根部门或在现有部门下创建子部门。 当前仅超级管理员可执行。'),
    ('system:dept:update', '编辑部门', '修改部门信息、上级部门或启用状态。 当前仅超级管理员可执行。'),
    ('system:dept:delete', '删除部门', '删除符合条件的部门节点。 当前仅超级管理员可执行。'),
    ('system:menu:list', null, '进入菜单管理，查看目录、页面及按钮权限。 当前仅超级管理员可执行。'),
    ('system:menu:query', '查询菜单', '预留标识。菜单列表当前由管理员权限控制。'),
    ('system:menu:create', '新增菜单或操作', '创建目录、页面入口或按钮权限。 当前仅超级管理员可执行。'),
    ('system:menu:update', '编辑菜单或操作', '调整菜单层级、显示状态、路由或权限标识。 当前仅超级管理员可执行。'),
    ('system:menu:delete', '删除菜单或操作', '删除符合条件的菜单或按钮权限。 当前仅超级管理员可执行。'),
    ('audit:login:list', null, '查看登录账号、时间、来源 IP 和成功或失败结果。 当前仅超级管理员可执行。'),
    ('audit:operation:list', null, '查看操作人、操作类型、目标对象及执行结果。 当前仅超级管理员可执行。')
) as seed(permission_code, display_name, description)
where menu.permission_code = seed.permission_code;
