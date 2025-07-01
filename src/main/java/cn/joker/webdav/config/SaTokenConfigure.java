package cn.joker.webdav.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.hooks.SaFirewallCheckHookForHttpMethod;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/**", "/api/public/**", r -> {
                StpUtil.checkLogin();
            });

        })).addPathPatterns("/**");
    }

    @PostConstruct
    public void getSaFirewallCheckHookForHttpMethod() {
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("PROPFIND");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("MKCOL");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("LOCK");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("UNLOCK");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("MOVE");
        SaFirewallCheckHookForHttpMethod.instance.allowMethods.add("COPY");
    }
}
