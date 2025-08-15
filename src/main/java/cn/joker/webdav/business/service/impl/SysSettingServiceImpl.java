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
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class SysSettingServiceImpl implements ISysSettingService {

    @Autowired
    private IKeyValueService keyValueService;

    @Autowired
    private TaskManager taskManager;

    @PostConstruct
    public void init() {
        SysSetting sysSetting = get();
        taskManager.setMaxPermits(Integer.parseInt(sysSetting.getTaskCopyNumber()));
    }

    @Override
    public void save(SysSetting sysSetting) {
        keyValueService.updateBusinessData("sysSetting", sysSetting, SysSetting.class);

        int poolSize = Integer.parseInt(sysSetting.getTaskCopyNumber());
        taskManager.setMaxPermits(poolSize);
    }


    @Override
    public SysSetting get() {
        List<SysSetting> list = keyValueService.findBusinessAll("sysSetting", SysSetting.class);
        return list.getFirst();
    }
}
