package cn.joker.webdav.business.service.impl;

import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.database.service.IKeyValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysSettingServiceImpl implements ISysSettingService {

    @Autowired
    private IKeyValueService keyValueService;

    @Override
    public void save(SysSetting sysSetting) {
        keyValueService.updateBusinessData("sysSetting", sysSetting, SysSetting.class);
    }

    @Override
    public SysSetting get() {
        List<SysSetting> list = keyValueService.findBusinessAll("sysSetting", SysSetting.class);
        return list.get(0);
    }
}
