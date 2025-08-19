package cn.joker.webdav.cache;

import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.webdav.entity.FileResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class FileTreeCacheService {

    @Autowired
    private CacheManager cacheManager;


    public void put(String path, List<FileResource> fileResourceList) {
        path = PathUtils.normalizePath(path);
        cacheManager.getCache("filePathCache").put(path, fileResourceList);
    }

    public List<FileResource> get(String path) {
        path = PathUtils.normalizePath(path);
        return cacheManager.getCache("filePathCache").get(path, ArrayList::new);
    }

    public void remove(String path) {
        path = PathUtils.normalizePath(path);
        cacheManager.getCache("filePathCache").evict(path);
    }
}
