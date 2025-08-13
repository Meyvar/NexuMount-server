package cn.joker.webdav.fileTask;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "fileTransferExecutor")
    public ThreadPoolTaskExecutor fileTransferExecutor() {
        int poolSize = 5;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("FileTransfer-");
        executor.initialize();
        return executor;
    }

}
