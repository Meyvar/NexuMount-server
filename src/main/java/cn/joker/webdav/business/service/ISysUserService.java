package cn.joker.webdav.business.service;

import cn.joker.webdav.business.entity.SysUser;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISysUserService extends IService<SysUser> {

    SysUser login(String username, String password);

}
