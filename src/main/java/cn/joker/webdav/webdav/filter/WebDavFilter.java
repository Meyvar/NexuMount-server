package cn.joker.webdav.webdav.filter;


import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.service.ISysUserService;
import cn.joker.webdav.webdav.service.IWebDavService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Base64;

@Component
@Order(0)
public class WebDavFilter extends OncePerRequestFilter {

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private IWebDavService webDavService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String ua = request.getHeader("User-Agent");

        String depth = request.getHeader("depth");

        String basic = request.getHeader("Authorization");

        if (
                StringUtils.hasText(depth)
                        || StringUtils.hasText(basic)
                        || (StringUtils.hasText(ua) && ua.contains("WebDAV"))
                        || request.getHeader("cookie").contains("webdav-key")
        ) {

            if (!StringUtils.hasText(basic)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Basic realm=\"\"");
                return;
            } else {

                if (request.getHeader("cookie") != null && request.getHeader("cookie").contains("webdav-key")) {

                    Cookie[] cookies = request.getCookies();
                    if (cookies != null) {
                        for (Cookie cookie : cookies) {
                            if (cookie.getName().equals("webdav-key")) {
                                basic = "Basic " + cookie.getValue();
                                break;
                            }
                        }
                    }
                }


                String authorization = basic;
                basic = basic.replace("Basic ", "");
                basic = new String(Base64.getDecoder().decode(basic));
                String[] user = basic.split(":");
                try {
                    sysUserService.login(user[0], user[1]);
                    response.addCookie(new Cookie("webdav-key", authorization.replace("Basic ", "")));
                } catch (Exception e) {
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("WWW-Authenticate", "Basic realm=\"\"");
                    return;
                }
            }

            try {
                webDavService.sendContent();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        filterChain.doFilter(request, response);
    }
}
