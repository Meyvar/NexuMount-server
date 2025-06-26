package cn.joker.webdav.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.hooks.SaFirewallCheckHookForHttpMethod;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.service.IWebDavService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Autowired
    private IWebDavService webDavService;

    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                .addExclude(
                        "/favicon.ico",
                        "/index.html",
                        "/js/**",
                        "/img/**",
                        "/icons/**",
                        "/css/**"
                )
                .addInclude("/**")
                .setAuth(obj -> {
                    SaRouter.match("/api/public/**").check(r -> {
                        // 公共接口，无需认证
                    });
                    SaRouter.match("/api/pub/**").check(r -> {
                        // 登录即可访问的接口
                        StpUtil.checkLogin();
                    });
                    SaRouter.match("/**").check(r -> {

                        HttpServletRequest req = RequestHolder.getRequest();
                        String sfd = req.getHeader("sec-fetch-dest");

                        if (!StringUtils.hasText(sfd)) {
                            try {
                                webDavService.sendContent();
                                r.back();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        // 其他接口需要登录并验证权限
                        StpUtil.checkLogin();
                        // 获取当前接口路径
                        String path = SaHolder.getRequest().getRequestPath();
                        // 校验是否有对应权限
                        StpUtil.checkPermission("api:" + path);


                    });
                })
                .setError(e -> {
                    return e.getMessage();
                });
    }

    @PostConstruct
    public void getSaFirewallCheckHookForHttpMethod() {
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("PROPFIND");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("MKCOL");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("LOCK");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("UNLOCK");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("MOVE");
    }
}
