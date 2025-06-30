package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileRessource;
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
    public FileRessource getFolderItself(String path) {
        FileRessource fileRessource = new FileRessource();
        fileRessource.setType("folder");
        fileRessource.setName(path);
        fileRessource.setSize(0L);
        fileRessource.setDate(new Date());
        return fileRessource;
    }

    @Override
    public List<FileRessource> propFind(String path, String uri) {
        CacheManager cacheManager = SprintContextUtil.getApplicationContext().getBean("cacheManager", CacheManager.class);
        Cache<Object, Object> nativeCache = ((CaffeineCache) cacheManager.getCache("fileBucketList")).getNativeCache();


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        List<FileRessource> list = new ArrayList<>();
        nativeCache.asMap().forEach((key, value) -> {
            FileBucket fileBucket = (FileBucket) value;

            String mountPath = (String) key;
            mountPath = mountPath.substring(1);
            if (!mountPath.contains("/") && StringUtils.hasText(mountPath)){
                FileRessource ressource = new FileRessource();
                ressource.setName(mountPath);
                ressource.setType("folder");
                try {
                    ressource.setDate(format.parse(fileBucket.getUpdateTime()));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                list.add(ressource);
            }
        });

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
