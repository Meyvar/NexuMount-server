package cn.joker.webdav.business.entity;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

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

    private JSONObject fieldJson =  new JSONObject();
}
