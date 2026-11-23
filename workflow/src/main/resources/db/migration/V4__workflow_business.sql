create table wf_business (
    code varchar(64) primary key,
    name varchar(128) not null
);
create table wf_business_grant (
    business_code varchar(64) not null references wf_business(code) on delete cascade,
    user_id bigint not null references sys_user(id) on delete cascade,
    can_edit boolean not null,
    can_publish boolean not null,
    can_start boolean not null,
    primary key (business_code,user_id),
    check (can_edit or can_publish or can_start)
);
insert into sys_menu(name,type,route_name)
values ('流程管理','DIRECTORY','WorkflowManagement');
insert into sys_menu(parent_id,name,type,route_name,path,component_key,permission_code)
select id,'流程模板','PAGE','WorkflowTemplates','/workflow/templates','workflow/templates','workflow:template:list'
from sys_menu where route_name='WorkflowManagement';
update sys_menu set route_name=null where route_name='WorkflowManagement' and type='DIRECTORY';
