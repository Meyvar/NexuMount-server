package cn.joker.webdav.business.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.KeyUtil;
import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.result.Response;
import cn.joker.webdav.business.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;

import static cn.joker.webdav.result.Response.success;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Autowired
    private ISysUserService sysUserService;

    @Autowired
    private ISysSettingService sysSettingService;

    @RequestMapping("/login.do")
    public Response<SysUser> login(String username, String password){
        return Response.success(sysUserService.login(username, password));
    }

    @RequestMapping("/logout.do")
    public Response<SysUser> logout(){
        StpUtil.logout();
        return Response.success();
    }


    @RequestMapping("/getAesKey.do")
    public Response<String> getAesKey(){
        SecretKey key = KeyUtil.generateKey("AES", 128);
        Response<String> response = Response.success();
        response.setData(Base64.encode(key.getEncoded()));
        return response;
    }

    @RequestMapping("/getWebConfig.do")
    public Response<SysSetting> getWebConfig() {
        return success(sysSettingService.get());
    }
}
