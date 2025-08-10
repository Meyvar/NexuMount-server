package cn.joker.webdav.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.joker.webdav.result.Response;
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
        if (e instanceof NotLoginException) {
            response.setCode(Response.NO_LOGIN);
        } else if (e instanceof NotPermissionException) {
            response.setCode(Response.NO_PERMISSIONS);
        } else {
            response.setCode(Response.CODE_ERROR);
        }
        return response;
    }

}
