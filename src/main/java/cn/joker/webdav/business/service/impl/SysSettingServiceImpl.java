package cn.joker.webdav.business.service.impl;

import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.database.service.IKeyValueService;
import cn.joker.webdav.fileTask.TaskManager;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class SysSettingServiceImpl implements ISysSettingService {

    @Autowired
    private IKeyValueService keyValueService;

    @Qualifier("fileTransferExecutor")
    @Autowired
    private ThreadPoolTaskExecutor executor;

    @Override
    public void save(SysSetting sysSetting) {
        keyValueService.updateBusinessData("sysSetting", sysSetting, SysSetting.class);

        int poolSize = Integer.parseInt(sysSetting.getTaskCopyNumber());
        updatePoolSize(executor, poolSize);
    }

    public void updatePoolSize(ThreadPoolTaskExecutor executor, int newSize) {
        if (newSize < 0) {
            throw new IllegalArgumentException("线程池大小不能小于 0");
        }

        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        if (pool == null) {
            // executor 还未初始化（或尚未创建底层 ThreadPoolExecutor）
            // 将属性设置到 ThreadPoolTaskExecutor 上，初始化后会生效
            executor.setCorePoolSize(newSize);
            executor.setMaxPoolSize(newSize);
            return;
        }

        int currentCore = pool.getCorePoolSize();
        int currentMax  = pool.getMaximumPoolSize();

        try {
            if (newSize > currentMax) {
                // 扩容：先扩 maximum，再设置 core
                pool.setMaximumPoolSize(newSize);
                pool.setCorePoolSize(newSize);
            } else if (newSize < currentCore) {
                // 缩小并且 newSize 小于当前 core：先设置 core，再设置 maximum
                pool.setCorePoolSize(newSize);
                pool.setMaximumPoolSize(newSize);
            } else {
                // newSize 在 [currentCore, currentMax] 区间内，先设置 core 再设置 max（或只设置 one）
                pool.setCorePoolSize(newSize);
                pool.setMaximumPoolSize(newSize);
            }
        } catch (IllegalArgumentException e) {
            // 捕获并记录，避免抛出导致应用异常
            throw new IllegalArgumentException("更新任务数量失败!");
            // 你可以进一步记录日志或回退逻辑
        }
    }

    @Override
    public SysSetting get() {
        List<SysSetting> list = keyValueService.findBusinessAll("sysSetting", SysSetting.class);
        return list.getFirst();
    }
}
