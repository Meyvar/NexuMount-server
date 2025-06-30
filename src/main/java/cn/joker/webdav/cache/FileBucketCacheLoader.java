package cn.joker.webdav.cache;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.IFileBucketService;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileBucketCacheLoader implements ApplicationRunner {

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired
    private IFileBucketService fileBucketService;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        List<FileBucket> bucketList = fileBucketService.findAll();

        bucketList.forEach(bucket -> {
            bucket.setPath("/" + bucket.getPath());
        });

        boolean hasRoot = false;
        for (FileBucket bucket : bucketList) {
            if ("/".equals(bucket.getPath())) {
                hasRoot = true;
            }
        }

        if (!hasRoot) {
            FileBucket root = new FileBucket();
            root.setPath("/");
            root.setAdapter("rootAdapter");
            root.setSourcePath("");
            bucketList.add(root);
        }


        Cache cache = cacheManager.getCache("fileBucketList");

        if (cache != null) {
            cache.clear();
            for (FileBucket bucket : bucketList) {
                cache.put(bucket.getPath(), bucket);
            }
        }

    }
}
