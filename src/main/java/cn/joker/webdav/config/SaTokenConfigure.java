package cn.joker.webdav.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

//    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                .addInclude("/**")
                .addExclude(
                        "/favicon.ico",
                        "/index.html",
                        "/js/**",
                        "/img/**",
                        "/icons/**",
                        "/css/**"
                )
                .setAuth(obj -> {
//                    SaRouter.match("/api/public/**").check(r -> {
//                        // 公共接口，无需认证
//                    });
//                    SaRouter.match("/api/pub/**").check(r -> {
//                        // 登录即可访问的接口
//                        StpUtil.checkLogin();
//                    });
//                    SaRouter.match("/**").check(r -> {
//                        // 其他接口需要登录并验证权限
//                        StpUtil.checkLogin();
//                        // 获取当前接口路径
//                        String path = SaHolder.getRequest().getRequestPath();
//                        // 校验是否有对应权限
//                        StpUtil.checkPermission("api:" + path);
//                    });
                })
                .setError(e -> {
                    return e.getMessage();
                });
    }
}
