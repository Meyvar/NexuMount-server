package cn.joker.webdav;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class WebDavServerApplication {

    public static void main(String[] args) {
        // 添加JVM参数，允许访问com.sun.jndi.ldap包
        System.setProperty("com.sun.jndi.ldap.object.trustURLCodebase", "true");
        System.setProperty("com.sun.jndi.cosnaming.object.trustURLCodebase", "true");
        
        SpringApplication.run(WebDavServerApplication.class, args);
    }
}
