package cn.joker.webdav.webdav.service;

import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IApiService {

    List<FileResource> list(RequestParam param);

    void delete(RequestParam param);

    FileResource get(RequestParam param);

    void load(RequestParam param);

    void download(RequestParam param);

    void packageDownload(List<String> list, String fileName);

    void upload(MultipartFile file, String path, String toPath);

    void createFolder(RequestParam param);

    void createFile(RequestParam param);

    void move(RequestParam param);

    void copy(RequestParam param);
}
