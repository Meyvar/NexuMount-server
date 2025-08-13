package cn.joker.webdav.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class ExternalConfig {

    @Value("${storage.target-path}")
    private String targetPath;

}
