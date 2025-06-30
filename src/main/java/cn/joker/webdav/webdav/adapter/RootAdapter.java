package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Component
public class RootAdapter implements IFileAdapter {

    @Override
    public boolean hasPath(Path path) {
        return true;
    }

    @Override
    public FileResource getFolderItself(String path) {
        FileResource fileResource = new FileResource();
        fileResource.setType("folder");
        fileResource.setName("");
        fileResource.setSize(0L);
        fileResource.setDate(new Date());
        fileResource.setHref(path);
        return fileResource;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri) {
        CacheManager cacheManager = SprintContextUtil.getApplicationContext().getBean("cacheManager", CacheManager.class);
        Cache<Object, Object> nativeCache = ((CaffeineCache) cacheManager.getCache("fileBucketList")).getNativeCache();


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<FileResource> list = new ArrayList<>();
        nativeCache.asMap().forEach((key, value) -> {
            FileBucket bucket = (FileBucket) value;

            String mountPath = (String) key;
            mountPath = mountPath.substring(1);
            if (!mountPath.contains("/") && StringUtils.hasText(mountPath)){
                FileResource resource = new FileResource();
                resource.setName(mountPath);
                resource.setHref("webdav/" + mountPath);
                resource.setSize(0L);
                resource.setType("folder");
                try {
                    resource.setDate(format.parse(bucket.getUpdateTime()));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                list.add(resource);
            }
        });

        list.addFirst(getFolderItself(fileBucket.getPath()));

        return list;
    }

    @Override
    public void get(Path path) {

    }

    @Override
    public void put(Path path) {

    }

    @Override
    public void delete(Path path) {

    }

    @Override
    public void mkcol(Path path) {

    }

    @Override
    public void move(Path sourcePath) {

    }
}
