package cn.joker.webdav.business.controller;

import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.result.Response;
import cn.joker.webdav.business.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.joker.webdav.result.Response.success;

@RestController
@RequestMapping("/api/sysUser")
public class SysUserController {

    @Autowired
    private ISysUserService sysUserService;

    @RequestMapping("/list.do")
    public Response<List<SysUser>> list() {
        return success(sysUserService.findAll());
    }


    @RequestMapping("/save.do")
    public Response<String> save(SysUser sysUser) {
        sysUserService.save(sysUser);
        return success();
    }

    @RequestMapping("/delete.do")
    public Response<String> delete(String uuid) {
        sysUserService.delete(uuid);
        return success();
    }
}
