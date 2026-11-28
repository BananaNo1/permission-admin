alter table sys_dept add column manager_user_id bigint references sys_user(id) on delete set null;
create index idx_dept_manager_user on sys_dept (manager_user_id);
