package cn.joker.webdav.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.strategy.hooks.SaFirewallCheckHookForHttpMethod;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.service.IWebDavService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Autowired
    private IWebDavService webDavService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/**", "/api/public/**", r -> {

                HttpServletRequest req = RequestHolder.getRequest();
                String sfd = req.getHeader("sec-fetch-dest");

                String depth = req.getHeader("depth");

                if (true) {
//                if (!StringUtils.hasText(sfd) || StringUtils.hasText(depth)) {
                    try {
                        webDavService.sendContent();
                        SaRouter.back();
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

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
    }
}
