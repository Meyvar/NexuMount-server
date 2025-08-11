package cn.joker.webdav.fileTask;

import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.service.ISysSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {
//
//    @Autowired
//    private ISysSettingService sysSettingService;

    @Bean(name = "fileTransferExecutor")
    public ThreadPoolTaskExecutor fileTransferExecutor() {
//        SysSetting sysSetting = sysSettingService.get();
//        int poolSize = Integer.parseInt(sysSetting.getTaskCopyNumber());
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
