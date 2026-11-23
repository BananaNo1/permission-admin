# 后端修复验证记录

2026-11-23

已修复组件及 Mapper 扫描、安全配置注解、登录状态拼写、角色主键查询、多角色数据范围、管理员初始化事务及权限异常类型。保留当前包名，通过启动工程扫描配置排除两个库工程的独立启动配置。为已有生成测试补齐 common、permission 的测试依赖。

| 检查 | 命令 | 退出码 | 结果 |
| --- | --- | --- | --- |
| 构建 | mvn -o -DskipTests package | 0 | 四个工程成功，17.158 秒；编译了已有测试，跳过测试执行 |
| 已有测试 | mvn -o test | 1 | common 通过；permission 的启动测试缺少数据源配置/驱动，backend 未执行，9.665 秒 |
| 启动 | java -jar backend/target/backend-0.1.0-SNAPSHOT.jar --server.port=18080 | 1 | PostgreSQL localhost:5432 拒绝连接，约7.5秒；Redis Windows 服务正在运行 |
| 外部HTTP | ./tests/backend/auth-smoke.ps1 | 1 | 18080端口未就绪，2.152秒；接口断言未完成 |
| Git diff | git status --short | 非零 | 当前目录未初始化 Git |

数据库就绪后，从工程根目录执行下面的真实HTTP检查。PowerShell 7 环境下运行，默认检查匿名CSRF、匿名身份请求和缺失CSRF的写请求。设置 AUTH_TEST_USERNAME 与 AUTH_TEST_PASSWORD 后，会额外验证测试账号登录、登录后会话保留、注销和旧会话失效。凭据从环境读取。

```powershell
./tests/backend/auth-smoke.ps1 -BaseUrl http://127.0.0.1:18080
```

当前尚未验证数据库上的角色查询、多角色范围、首次管理员初始化回滚、真实登录与前端业务流程。permission/src/main/resources 中尚未发现初始化SQL，运行前还需要确认实际数据库已具备系统表与内置角色。