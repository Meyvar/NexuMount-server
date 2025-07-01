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

import java.io.InputStream;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Component
public class RootAdapter implements IFileAdapter {

    @Override
    public boolean hasPath(String path) {
        return true;
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) {
        FileResource fileResource = new FileResource();
        fileResource.setType("folder");
        fileResource.setName("");
        fileResource.setSize(0L);
        fileResource.setDate(new Date());
        fileResource.setHref(uri);
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
                resource.setHref(mountPath);
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

        return list;
    }

    @Override
    public InputStream get(String path) {
        return null;
    }

    @Override
    public void put(String path) {

    }

    @Override
    public void delete(String path) {

    }

    @Override
    public void mkcol(String path) {

    }

    @Override
    public void move(Path sourcePath) {

    }
}
