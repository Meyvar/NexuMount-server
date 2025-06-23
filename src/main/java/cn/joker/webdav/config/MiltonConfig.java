package cn.joker.webdav.config;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.ResourceFactory;
import io.milton.http.fs.FileSystemResourceFactory;
import io.milton.http.fs.NullSecurityManager;
import io.milton.http.http11.DefaultHttp11ResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
public class MiltonConfig {
    @Bean
    public ResourceFactory resourceFactory() {
        String path = "webdav";
        FileSystemResourceFactory factory = new FileSystemResourceFactory();
        factory.setAllowDirectoryBrowsing(true);
        factory.setRoot(new File(path));
        factory.setSecurityManager(new NullSecurityManager());
        ensureDataDirectory(path);
        return factory;
    }

    @Bean
    public HttpManagerBuilder httpManagerBuilder() {
        HttpManagerBuilder builder = new HttpManagerBuilder();
        builder.setResourceFactory(resourceFactory());
        builder.setBuffering(DefaultHttp11ResponseHandler.BUFFERING.whenNeeded);
        builder.setEnableCompression(false);
        return builder;
    }

    /**
     * 确保存放数据的目录存在
     */
    private static void ensureDataDirectory(String path) {
        File file = new File(path);
        if (file.exists()) {
            return;
        }
        boolean b = file.mkdir();
        if (!b) {
            throw new IllegalStateException("create directory fail");
        }
    }
}
