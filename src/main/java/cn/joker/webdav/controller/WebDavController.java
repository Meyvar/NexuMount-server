package cn.joker.webdav.controller;

import io.milton.config.HttpManagerBuilder;
import io.milton.servlet.ServletRequest;
import io.milton.servlet.ServletResponse;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class WebDavController {

    @Autowired
    private HttpManagerBuilder httpManagerBuilder;

    @Autowired
    private ServletContext servletContext;

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public void options(HttpServletResponse resp) {
        resp.setHeader("Allow", "OPTIONS, GET, PUT, DELETE, PROPFIND, MKCOL");
        resp.setHeader("DAV", "1,2");
        resp.setHeader("MS-Author-Via", "DAV");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    @RequestMapping(value = "/**")
    public void webdav(HttpServletRequest req, HttpServletResponse resp) {
        String method = req.getMethod();

        ServletRequest miltonRequest = new ServletRequest(req, servletContext);
        ServletResponse miltonResponse = new ServletResponse(resp);
        httpManagerBuilder.buildHttpManager().process(miltonRequest, miltonResponse);
    }

}
