package cn.joker.webdav.business.entity;

import lombok.Data;

import java.util.Date;

@Data
public class FileBucket {

    private String uuid;

    private String path;

    private String sourcePath;

    private String adapter;

    private String sort;

    private String status;

    private String encryption;

    private String aesKey;

    private String updateTime;
}
