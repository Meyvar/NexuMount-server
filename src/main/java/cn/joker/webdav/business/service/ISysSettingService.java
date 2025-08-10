package cn.joker.webdav.business.service;

import cn.joker.webdav.business.entity.SysSetting;

public interface ISysSettingService {

    void save(SysSetting sysSetting);

    SysSetting get();
}
