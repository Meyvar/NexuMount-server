package cn.joker.webdav.business.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.ISysUserService;
import cn.joker.webdav.database.entity.KeyValue;
import cn.joker.webdav.database.service.IKeyValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private IKeyValueService keyValueService;

    @Override
    public SysUser login(String username, String password) {
        password = DigestUtil.md5Hex(password);


        List<KeyValue> keyValueList = keyValueService.findListByKey("sysUser", "username", username);

        if (keyValueList.isEmpty()) {
            throw new RuntimeException("用户名或密码错误");
        } else if (keyValueList.size() > 1) {
            throw new RuntimeException("用户名重复");
        }

        SysUser sysUser = keyValueService.findBusinessData("sysUser", keyValueList.getFirst().getBelong(), SysUser.class);

        if (sysUser == null || !sysUser.getPassword().equals(password)) {
            throw new RuntimeException("用户名或密码错误");
        }
        StpUtil.login(sysUser.getUuid());

        return sysUser;
    }

    @Override
    public List<SysUser> findAll() {
        List<SysUser> list = keyValueService.findBusinessAll("sysUser", SysUser.class);
        list.forEach(sysUser -> {
            sysUser.setPassword("");
        });
        return list;
    }

    @Override
    public void save(SysUser sysUser) {
        if (StringUtils.hasText(sysUser.getPassword())) {
            sysUser.setPassword(DigestUtil.md5Hex(sysUser.getPassword()));
        } else {
            sysUser.setPassword(null);
        }
        if (!StringUtils.hasText(sysUser.getUuid())) {
            keyValueService.saveBusinessData("sysUser", sysUser, SysUser.class);
        } else {
            keyValueService.updateBusinessData("sysUser", sysUser, SysUser.class);
        }
    }

    @Override
    public void delete(String uuid) {
        keyValueService.deleteBusinessData("sysUser", uuid);
    }

    @Override
    public SysUser getById(String uuid) {
        return  keyValueService.findBusinessData("sysUser", uuid, SysUser.class);
    }

}
