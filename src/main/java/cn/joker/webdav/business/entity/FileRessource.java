package cn.joker.webdav.business.entity;

import lombok.Data;

import java.util.Date;

@Data
public class FileRessource {

    private String name;

    private Integer size;

    private Date date;

    private String type;

}
