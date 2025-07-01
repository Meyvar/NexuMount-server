package cn.joker.webdav.webdav.filter;


import cn.dev33.satoken.router.SaRouter;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.service.IWebDavService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(0)
public class WebDavFilter extends OncePerRequestFilter {


    @Autowired
    private IWebDavService webDavService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String sfd = request.getHeader("sec-fetch-dest");

        String depth = request.getHeader("depth");

        String basic = request.getHeader("Authorization");

        if (!StringUtils.hasText(sfd) || StringUtils.hasText(depth) || StringUtils.hasText(basic)) {

            if (!StringUtils.hasText(basic)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setHeader("WWW-Authenticate", "Basic realm=\"\"");
                return;
            }

            try {
                webDavService.sendContent();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        filterChain.doFilter(request, response);
    }
}
