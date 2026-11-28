insert into sys_menu (parent_id, name, type, route_name, path, component_key, permission_code, sort_order)
select parent_id, feature.name, 'PAGE', feature.route_name, feature.path, feature.component_key,
       feature.permission_code, feature.sort_order
from sys_menu
cross join (values
    ('我的待办', 'WorkflowInboxTasks', '/workflow/tasks', 'workflow/tasks', 'workflow:inbox:tasks', 20),
    ('我的已办', 'WorkflowInboxCompleted', '/workflow/completed', 'workflow/completed', 'workflow:inbox:completed', 30),
    ('我的申请', 'WorkflowInboxApplications', '/workflow/applications', 'workflow/applications', 'workflow:inbox:applications', 40)
) as feature(name, route_name, path, component_key, permission_code, sort_order)
where sys_menu.permission_code = 'workflow:template:list';
