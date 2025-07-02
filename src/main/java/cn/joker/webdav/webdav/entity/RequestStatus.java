package cn.joker.webdav.webdav.entity;

import lombok.Data;

import java.util.List;

@Data
public class RequestStatus {

    private int code;

    private String message;

    private boolean success = false;

    List<FileResource>  fileResources;
}
