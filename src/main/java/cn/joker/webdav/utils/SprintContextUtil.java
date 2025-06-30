package cn.joker.webdav.utils;

import com.google.common.primitives.Primitives;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SprintContextUtil implements ApplicationContextAware {
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SprintContextUtil.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }


    public static <T> T getBean(String name, Class<T> classOfT) {
        Object object = applicationContext.getBean(name);
        return Primitives.wrap(classOfT).cast(object);
    }
}
