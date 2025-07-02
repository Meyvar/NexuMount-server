package cn.joker.webdav.webdav.service;

import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestParam;

import java.util.List;

public interface IApiService {

    List<FileResource> list(RequestParam param);

    void delete(RequestParam param);
}
