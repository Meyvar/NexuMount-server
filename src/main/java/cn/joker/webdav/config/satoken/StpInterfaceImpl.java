package cn.joker.webdav.config.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private ISysUserService sysUserService;

    @Override
    public List<String> getPermissionList(Object o, String s) {
        SysUser sysUser = sysUserService.getById((String) o);
        return Arrays.asList(sysUser.getPermissions().split(","));
    }

    @Override
    public List<String> getRoleList(Object o, String s) {
        return List.of();
    }
}
