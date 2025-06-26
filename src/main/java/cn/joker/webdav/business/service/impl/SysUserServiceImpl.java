package cn.joker.webdav.business.service.impl;

//import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.mapper.SysUserMapper;
import cn.joker.webdav.business.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements ISysUserService {

    @Override
    public SysUser login(String username, String password) {
        password = DigestUtil.md5Hex(password);

        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(SysUser::getUsername, username).eq(SysUser::getPassword, password);

        List<SysUser> userList = this.list(queryWrapper);

        if (userList.isEmpty()) {
            throw new RuntimeException("用户名或密码错误");
        } else if (userList.size() > 1) {
            throw new RuntimeException("用户名重复");
        }

        SysUser sysUser = userList.getFirst();

//        StpUtil.login(sysUser.getUuid());

        return sysUser;
    }

}
