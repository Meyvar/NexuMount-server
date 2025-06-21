package cn.joker.webdav.config;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.ResourceFactory;
import io.milton.http.Response;
import io.milton.http.annotated.AnnotationResourceFactory;
import io.milton.http.template.JspViewResolver;
import io.milton.http.template.ViewResolver;
import io.milton.servlet.MiltonServlet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.GenericFilterBean;

import java.io.IOException;

public class SpringMiltonFilterBean extends GenericFilterBean {

    @Autowired
    private HttpManagerBuilder httpManagerBuilder;

    private HttpManager httpManager;

    @Override
    protected void initFilterBean() throws ServletException {
        super.initFilterBean();
        ResourceFactory rf = httpManagerBuilder.getMainResourceFactory();
        if (rf instanceof AnnotationResourceFactory) {
            AnnotationResourceFactory arf = (AnnotationResourceFactory) rf;
            if (arf.getViewResolver() == null) {
                ViewResolver viewResolver = new JspViewResolver(this.getServletContext());
                arf.setViewResolver(viewResolver);
            }
        }
        this.httpManager = httpManagerBuilder.buildHttpManager();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 1.不是http请求
        if (!(servletRequest instanceof HttpServletRequest)) {
            chain.doFilter(servletRequest, response);
            return;
        }

        // 2.是http请求，访问的特殊文件路径，直接当做普通请求
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String url = request.getRequestURI();

        // 3.是http请求，访问的普通路径，走webdav协议
        doMiltonProcessing(request, (HttpServletResponse) response);
    }

    private void doMiltonProcessing(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            MiltonServlet.setThreadlocals(req, resp);
            Request request = new io.milton.servlet.ServletRequest(req, this.getServletContext());
            Response response = new io.milton.servlet.ServletResponse(resp);
            httpManager.process(request, response);
        } finally {
            MiltonServlet.clearThreadlocals();
            resp.flushBuffer();
        }
    }

    @Override
    public void destroy() {
        if (httpManager != null) {
            httpManager.shutdown();
        }
    }

}
