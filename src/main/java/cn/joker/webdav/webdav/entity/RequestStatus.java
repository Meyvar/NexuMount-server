package cn.joker.webdav.webdav.entity;

import lombok.Data;

@Data
public class RequestStatus {

    private int code;

    private String message;

    private boolean success = false;

}
