update sys_menu as menu
set description = seed.description
from (values
    ('workflow:template:list', '配置审批节点、管理业务授权和发布流程'),
    ('workflow:inbox:tasks', '查看并办理分配给我的审批任务'),
    ('workflow:inbox:completed', '查询我已办理的任务和办理结果'),
    ('workflow:inbox:applications', '发起审批并跟踪我的申请进度'),
    ('workflow:operations:list', '查看在途流程、处理引擎异常和终止实例')
) as seed(permission_code, description)
where menu.permission_code = seed.permission_code
    and menu.type = 'PAGE'
    and menu.description is null;
