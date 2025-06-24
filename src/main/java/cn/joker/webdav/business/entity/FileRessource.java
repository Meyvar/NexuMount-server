package cn.joker.webdav.business.entity;

import lombok.Data;

import java.util.Date;

@Data
public class FileRessource {

    private String name;

    private Long size;

    private Date date;

    private String type;

}
