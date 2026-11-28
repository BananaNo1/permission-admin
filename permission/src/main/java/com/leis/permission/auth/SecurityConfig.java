package com.leis.permission.auth;

import com.leis.common.common.ApiResponse;
import com.leis.permission.SessionUser;
import com.leis.permission.log.service.AuditService;
import com.leis.permission.user.entity.User;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.util.Locale;

/**
 * 装配登录、退出、访问控制和错误响应所需的 Spring Security 过滤链。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 创建按编码前缀识别算法的密码编码器，供认证和密码写入使用。
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * 归一化账号后查询用户，仅将启用用户转换为认证主体。
     */
    @Bean
    UserDetailsService userDetailsService(AccountQueryService accountQueryService) {
        return name -> {
            User user = accountQueryService.findByUsername(name.trim().toLowerCase(Locale.ROOT));
            if (user == null || !"ENABLED".equals(user.getStatus())) {
                throw new UsernameNotFoundException("账号或密码错误");
            }
            return new SessionUser(user.getId(), user.getUsername(), user.getPasswordHash(), user.getAuthVersion());
        };
    }


    /**
     * 配置登录和退出入口、会话过滤器以及未认证和无权限请求的处理方式。
     */
    @Bean
    SecurityFilterChain security(HttpSecurity http, UserDetailsService details,
                                 PasswordEncoder encoder, AuthorizationService authorization, AuditService audit,
                                 LoginThrottle throttle, ApiResponseWriter responses)
            throws Exception {
        var provider = new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        http.authenticationProvider(provider);
        http.addFilterBefore(throttle.filter(responses), UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(new RequestFilter(authorization, audit, responses), SecurityContextHolderFilter.class);
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/csrf", "/api/auth/login", "/error").permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, e) -> responses.error(res, 401, "请先登录"))
                .accessDeniedHandler((req, res, e) -> responses.error(res, 403, "权限或 CSRF 校验失败")));

        http.formLogin(form -> form.loginProcessingUrl("/api/auth/login")
                .successHandler((req, res, auth) -> {
                    throttle.succeeded(req);
                    var user = (SessionUser) auth.getPrincipal();
                    audit.login(user.username(), user.id(), true, req.getRemoteAddr(), (String) req.getAttribute(
                            "traceId"));
                    responses.write(res, ApiResponse.ok());
                })
                .failureHandler((req, res, e) -> {
                    throttle.failed(req);
                    String name = req.getParameter("username");
                    name = name == null ? "" : name.substring(0, Math.min(name.length(), 32));
                    audit.login(name, null, false, req.getRemoteAddr(), (String) req.getAttribute("traceId"));
                    responses.error(res, 401, "账号或密码错误");
                })
        );

        http.logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("SESSION")
                .logoutSuccessHandler((req, res, auth) -> {
                    responses.write(res, ApiResponse.ok());
                }));

        return http.build();
    }

}
