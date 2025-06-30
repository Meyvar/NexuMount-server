package cn.joker.webdav;

import cn.dev33.satoken.SaManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication
@EnableCaching
public class WebDavServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebDavServerApplication.class, args);
    }
}
