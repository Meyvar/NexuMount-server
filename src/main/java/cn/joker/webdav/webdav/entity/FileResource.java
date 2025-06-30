package cn.joker.webdav.webdav.entity;

import lombok.Data;

import java.util.Date;

@Data
public class FileResource {

    private String name;

    private Long size;

    private Date date;

    private String type;

    private String href;

}
