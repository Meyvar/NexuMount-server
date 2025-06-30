package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.trie.FileBucketPathUtils;
import cn.joker.webdav.webdav.adapter.trie.PathMatchResult;
import cn.joker.webdav.webdav.adapter.trie.PathTrie;
import cn.joker.webdav.webdav.entity.FileRessource;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.Getter;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdapterManager {

    private Path path;

    private String uri;

    private CacheManager cacheManager;

    private IFileAdapter adapter;

    private FileBucket fileBucket;

    private List<FileBucket> fileBucketList = new ArrayList<>();

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public AdapterManager(Path path, String uri) {
        this.path = path;

        cacheManager = SprintContextUtil.getApplicationContext().getBean("cacheManager", CacheManager.class);

        if ("/".equals(uri)) {
            fileBucket = (FileBucket) cacheManager.getCache("fileBucketList").get(uri).get();

            this.uri = uri.replaceAll(fileBucket.getPath(), "");
            if (!StringUtils.hasText(this.uri)) {
                this.uri = "/";
            }

            if ("rootAdapter".equals(fileBucket.getAdapter())) {
                adapter = SprintContextUtil.getBean("rootAdapter", IFileAdapter.class);
                return;
            }
        }

        Cache<Object, Object> nativeCache = ((CaffeineCache) cacheManager.getCache("fileBucketList")).getNativeCache();

        List<FileBucket> list = new ArrayList<>();



        nativeCache.asMap().forEach((key, value) -> {
            FileBucket fileBucket = (FileBucket) value;
            list.add(fileBucket);
        });

        PathMatchResult result = FileBucketPathUtils.matchSelfAndDirectChildren(path.toString(), list);


        fileBucket = FileBucketPathUtils.findLongestPrefix(path.toString(), list);
        fileBucketList = FileBucketPathUtils.findDirectChildren(path.toString(), list);

        this.uri = uri.replaceAll(fileBucket.getPath(), "");
        this.path = Path.of(this.uri);
        if (!StringUtils.hasText(this.uri)) {
            this.uri = "/";
        }


        adapter = SprintContextUtil.getBean(fileBucket.getAdapter(), IFileAdapter.class);
    }

    public FileRessource getFolderItself() {
        FileRessource fileRessource = new FileRessource();
        fileRessource.setType("folder");
        fileRessource.setSize(0L);


        try {
            if (fileBucket.getPath().equals(this.uri)) {
                fileRessource.setDate(format.parse(fileBucket.getUpdateTime()));
            } else {
                for (FileBucket bucket : fileBucketList) {
                    if (bucket.getPath().equals(this.uri)) {
                        fileRessource.setDate(format.parse(bucket.getUpdateTime()));
                        break;
                    }
                }
            }
        } catch (Exception e) {
//            e.printStackTrace();
        }

        if (fileRessource.getDate() == null) {
            fileRessource = adapter.getFolderItself(fileBucket.getSourcePath() + this.path.toString().replace(fileBucket.getPath(), ""));
        }
        fileRessource.setName(this.uri);
        return fileRessource;
    }

    public List<FileRessource> propFind() {
        if ("rootAdapter".equals(fileBucket.getAdapter())) {
            return adapter.propFind(path.toString(), uri);
        }

        List<FileRessource> list = adapter.propFind(fileBucket.getSourcePath(), uri);

        for (FileBucket bucket : fileBucketList) {
            FileRessource ressource = new FileRessource();
            ressource.setType("folder");

            try {
                ressource.setDate(format.parse(bucket.getUpdateTime()));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            String path = bucket.getPath().replace(this.fileBucket.getPath() + "/", "");

            String[] paths = path.split("/");

            if (paths.length > 0 && StringUtils.hasText(paths[0])) {
                ressource.setName(path);
                list.add(ressource);
            }
        }
        return list;
    }

}
