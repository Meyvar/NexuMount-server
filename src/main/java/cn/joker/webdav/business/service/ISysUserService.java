package cn.joker.webdav.business.service;

import cn.joker.webdav.business.entity.SysUser;

import java.util.List;

public interface ISysUserService {

    SysUser login(String username, String password);

    List<SysUser> findAll();

    void save(SysUser sysUser);

    void delete(String uuid);
}
