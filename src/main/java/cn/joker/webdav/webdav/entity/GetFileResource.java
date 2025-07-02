package cn.joker.webdav.webdav.entity;

import lombok.Data;

@Data
public class GetFileResource {

    String filePath;

    Long fileSize;

}
