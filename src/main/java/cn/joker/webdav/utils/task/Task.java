package cn.joker.webdav.utils.task;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.IFileBucketService;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.trie.FileBucketPathUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Component
public class Task {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private IFileBucketService fileBucketService;

    /**
     * 清空文件列表缓存
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void ClearFileListCache() {
        Cache cache = cacheManager.getCache("filePathCache");
        if (cache != null) {
            cache.clear();
        }
    }


    /**
     * 更新存储token
     */
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void refreshToken() {
        List<FileBucket> list = fileBucketService.findAll();

        for (FileBucket fileBucket : list) {
            IFileAdapter adapter = SprintContextUtil.getBean(fileBucket.getAdapter(), IFileAdapter.class);
            FileBucket bucket = adapter.refreshToken(fileBucket);
            if (bucket == null) {
                continue;
            }
            fileBucket.setFieldJson(bucket.getFieldJson());
            fileBucketService.save(fileBucket);
        }
    }
}
