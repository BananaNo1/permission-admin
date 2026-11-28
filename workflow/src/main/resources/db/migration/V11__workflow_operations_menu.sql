insert into sys_menu (parent_id, name, type, route_name, path, component_key, permission_code, sort_order)
select parent_id, '流程运维', 'PAGE', 'WorkflowOperations', '/workflow/operations', 'workflow/operations',
       'workflow:operations:list', 50
from sys_menu where permission_code = 'workflow:template:list';
