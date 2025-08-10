package cn.joker.webdav.business.controller;

import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.result.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.joker.webdav.result.Response.success;

@RestController
@RequestMapping("/api/sysSetting")
public class SysSettingController {

    @Autowired
    private ISysSettingService sysSettingService;

    @RequestMapping("/save.do")
    public Response<String> save(SysSetting sysSetting) {
        sysSettingService.save(sysSetting);
        return success();
    }

}
