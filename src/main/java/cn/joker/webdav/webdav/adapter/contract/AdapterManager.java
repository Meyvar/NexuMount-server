package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.trie.FileBucketPathUtils;
import cn.joker.webdav.webdav.entity.FileResource;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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


        fileBucket = FileBucketPathUtils.findLongestPrefix(uri, list);
        fileBucketList = FileBucketPathUtils.findDirectChildren(uri, list);

        this.uri = uri.replaceAll(fileBucket.getPath(), "");
        this.path = Path.of(this.uri);
        if (!StringUtils.hasText(this.uri)) {
            this.uri = "/";
        }


        adapter = SprintContextUtil.getBean(fileBucket.getAdapter(), IFileAdapter.class);
    }

    /**
     * 文件夹自身
     *
     * @return
     */
    public FileResource getFolderItself() {
        FileResource fileResource = new FileResource();
        fileResource.setType("folder");
        fileResource.setSize(0L);


        try {
            if (fileBucket.getPath().equals(this.uri)) {
                fileResource.setDate(format.parse(fileBucket.getUpdateTime()));
            } else {
                for (FileBucket bucket : fileBucketList) {
                    if (bucket.getPath().equals(this.uri)) {
                        fileResource.setDate(format.parse(bucket.getUpdateTime()));
                        break;
                    }
                }
            }
        } catch (Exception e) {
//            e.printStackTrace();
        }
        fileResource.setName(this.uri);

        if (fileResource.getDate() == null) {
            fileResource = adapter.getFolderItself(fileBucket.getPath() + uri);
        }
        return fileResource;
    }

    /**
     * ？获取文件列表
     *
     * @return
     */
    public List<FileResource> propFind() {
        if ("rootAdapter".equals(fileBucket.getAdapter())) {
            return adapter.propFind(fileBucket, uri);
        }

        List<FileResource> list = adapter.propFind(fileBucket, uri);

        for (FileBucket bucket : fileBucketList) {
            FileResource resource = new FileResource();
            resource.setType("folder");

            try {
                resource.setDate(format.parse(bucket.getUpdateTime()));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            String name = bucket.getPath().replace(this.fileBucket.getPath() + "/", "");
            resource.setName(name);

            resource.setHref(bucket.getPath().replace(this.fileBucket.getPath(), ""));

            list.add(resource);
        }
        return list;
    }

    public InputStream get() throws IOException {
        return adapter.get(fileBucket.getSourcePath() + uri);
    }

    public boolean hasPath() {
        return adapter.hasPath(fileBucket.getSourcePath() + uri);
    }

}
