package cn.joker.webdav.business.controller;

import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.result.Response;
import cn.joker.webdav.business.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Autowired
    private ISysUserService sysUserService;

    @RequestMapping("/login.do")
    public Response<SysUser> login(String username, String password){
        return Response.success(sysUserService.login(username, password));
    }

}
