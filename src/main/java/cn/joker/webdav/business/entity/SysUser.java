package cn.joker.webdav.business.entity;

import lombok.Data;

@Data
public class SysUser {

    private String uuid;

    private String nike;

    private String username;

    private String password;

    private String status;

    private String permissions;

    private String rootPath;
}
