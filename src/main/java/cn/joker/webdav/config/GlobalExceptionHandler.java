package cn.joker.webdav.config;

import cn.joker.webdav.business.result.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private final static Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);


    @ExceptionHandler(value = Exception.class)
    public Response<String> exceptionHandler(HttpServletRequest req, HttpServletResponse resp, Exception e) throws Exception {
        logger.error(e.getMessage(), e);
        String msg = e.getMessage();
        if (msg == null) {
            msg = "服务方法出错！";
        }
        Response<String> response = Response.error(msg);
        response.setCode(Response.CODE_ERROR);
        return response;
    }

}
