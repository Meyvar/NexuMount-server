package cn.joker.webdav.webdav.entity;

import lombok.Data;

@Data
public class RequestParam {

    private String path;

    private String method;

    private String token;

    private boolean refresh;
}
