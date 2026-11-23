package com.leis.permission.auth;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.leis.permission.dept.mapper.DeptMapper;
import com.leis.permission.role.entity.Role;
import com.leis.permission.role.mapper.RoleMapper;
import com.leis.permission.user.entity.User;
import com.leis.permission.user.entity.UserRole;
import com.leis.permission.user.mapper.UserMapper;
import com.leis.permission.user.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {

    private final UserMapper users;
    private final UserRoleMapper userRoles;
    private final RoleMapper roles;
    private final DeptMapper depts;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    /**
     * 注入管理员初始化所需的 Mapper、密码编码器及启动配置。
     */
    public Bootstrap(UserMapper users, UserRoleMapper userRoles, RoleMapper roles, DeptMapper depts,
                     PasswordEncoder encoder, @Value("${bootstrap.username}") String username,
                     @Value("${bootstrap.password}") String password) {
        this.users = users;
        this.userRoles = userRoles;
        this.roles = roles;
        this.depts = depts;
        this.encoder = encoder;
        this.username = username;
        this.password = password;
    }

    /**
     * 在用户表为空时创建受保护的初始管理员，并在同一事务中绑定内置角色。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        depts.lockTree();
        if (users.selectCount(null) > 0) {
            return;
        }
        if (!username.matches("[a-z][a-z0-9_]{2,31}")) {
            throw new IllegalArgumentException("首次启动需要设置管理员账号");
        }
        PasswordPolicy.validate(password);
        User user = new User();
        user.setUsername(username);
        user.setNickname("初始管理员");
        user.setDeptId(1L);
        user.setPasswordHash(encoder.encode(password));
        user.setStatus("ENABLED");
        user.setProtectedAccount(true);
        user.setAuthVersion(0L);
        user.setVersion(0);
        users.insert(user);
        Role root = roles.selectOne(new QueryWrapper<Role>().eq("code", "SUPER_ADMIN"));
        UserRole link = new UserRole();
        link.setUserId(user.getId());
        link.setRoleId(root.getId());
        userRoles.insert(link);
    }
}
