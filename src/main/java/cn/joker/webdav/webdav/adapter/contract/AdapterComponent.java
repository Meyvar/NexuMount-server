package cn.joker.webdav.webdav.adapter.contract;



import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface AdapterComponent {
    String title();
}
