package cn.joker.webdav.business.service.impl;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.IFileBucketService;
import cn.joker.webdav.database.service.IKeyValueService;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class FileBucketServiceImpl implements IFileBucketService {

    @Autowired
    private IKeyValueService keyValueService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private CacheManager cacheManager;

    @Override
    public List<FileBucket> findAll() {
        return keyValueService.findBusinessAll("fileBucket", FileBucket.class);
    }

    @Override
    public List<Map<String, String>> getFileAdapterList() {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(AdapterComponent.class);
        List<Map<String, String>> list = new ArrayList<>();

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Map<String, String> map = new HashMap<>();

            Object bean = entry.getValue();
            Class<?> clazz = bean.getClass();
            AdapterComponent annotation = clazz.getAnnotation(AdapterComponent.class);

            map.put("name", entry.getKey());
            map.put("title", annotation.title());

            list.add(map);
        }


        return list;
    }

    @Override
    public void save(FileBucket fileBucket) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        fileBucket.setUpdateTime(format.format(new Date()));
        if (StringUtils.hasText(fileBucket.getUuid())) {
            keyValueService.updateBusinessData("fileBucket", fileBucket, FileBucket.class);
        } else {
            keyValueService.saveBusinessData("fileBucket", fileBucket, FileBucket.class);
        }

        updateCache();
    }


    public void updateCache() {
        List<FileBucket> bucketList = findAll();

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


    @Override
    public void updateFileBucketStatus(List<String> bucketList) {
        for (String s : bucketList) {
            FileBucket fileBucket = keyValueService.findBusinessData("fileBucket", s, FileBucket.class);
            IFileAdapter adapter = SprintContextUtil.getApplicationContext().getBean(fileBucket.getAdapter(), IFileAdapter.class);
            String status = adapter.workStatus(fileBucket);
            fileBucket.setStatus(status);
            keyValueService.updateBusinessData("fileBucket", fileBucket, FileBucket.class);
        }
    }

    @Override
    public void delete(String uuid) {
        keyValueService.deleteBusinessData("fileBucket", uuid);
    }
}
