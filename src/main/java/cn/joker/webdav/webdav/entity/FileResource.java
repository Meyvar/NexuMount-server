package cn.joker.webdav.webdav.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class FileResource {

    private String name;

    private Long size;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date date;

    private String type;

    private String href;

    private String contentType;

    private String id;

    private String driveId;

    private List<FileResource> children = new ArrayList<>();

    public void addChild(FileResource child) {
        this.children.add(child);
    }

    public void removeChild(FileResource child) {
        this.children.remove(child);
    }
}
