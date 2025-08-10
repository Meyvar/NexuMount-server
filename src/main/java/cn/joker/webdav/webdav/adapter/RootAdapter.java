package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Component
public class RootAdapter implements IFileAdapter {

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
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
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) {
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
    public void get(FileBucket fileBucket, String path) {
    }

    @Override
    public void put(FileBucket fileBucket, String path,  Path tempFilePath) {

    }

    @Override
    public void delete(FileBucket fileBucket, String path) {

    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) {

    }

    @Override
    public void move(FileBucket fileBucket, String sourcePath, String destPath) throws IOException {

    }

    @Override
    public void copy(FileBucket fileBucket, String sourcePath, String destPath) throws IOException {

    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, String fileType) {
        return "";
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        return "";
    }
}
