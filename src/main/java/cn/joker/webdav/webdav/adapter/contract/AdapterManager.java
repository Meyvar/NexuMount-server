package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.trie.FileBucketPathUtils;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import cn.joker.webdav.webdav.entity.RequestStatus;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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
    public FileResource getFolderItself() throws IOException {
        FileResource fileResource = new FileResource();
        fileResource.setType("folder");
        fileResource.setSize(0L);


        try {
            if (fileBucket.getPath().equals(this.uri)) {
                String updateTime = fileBucket.getUpdateTime();
                if (StringUtils.hasText(updateTime)) {
                    fileResource.setDate(format.parse(updateTime));
                }
            } else {
                for (FileBucket bucket : fileBucketList) {
                    if (bucket.getPath().equals(this.uri)) {
                        fileResource.setDate(format.parse(bucket.getUpdateTime()));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        fileResource.setName(this.uri);

        if (fileResource.getDate() == null) {
            fileResource = adapter.getFolderItself(fileBucket, uri);
        }
        return fileResource;
    }

    /**
     * ？获取文件列表
     *
     * @return
     */
    public RequestStatus propFind() throws IOException {
        RequestStatus status = new RequestStatus();

        if (!hasPath()) {
            status.setCode(HttpServletResponse.SC_NOT_FOUND);
            return status;
        } else {
            status.setSuccess(true);
        }

        if ("rootAdapter".equals(fileBucket.getAdapter())) {
            status.setFileResources(adapter.propFind(fileBucket, uri));
            return status;
        }

        List<FileResource> list = adapter.propFind(fileBucket, uri);

        HttpServletRequest request = RequestHolder.getRequest();
        String depth = request.getHeader("depth");

        list.add(getFolderItself());

        if ("1".equals(depth)) {

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

                resource.setHref(bucket.getPath());

                resource.setSize(0L);

                list.add(resource);
            }
        }

        status.setFileResources(list);
        return status;
    }

    public GetFileResource get() throws IOException {
        return adapter.get(fileBucket.getSourcePath() + uri);
    }

    public boolean hasPath() {
        return adapter.hasPath(fileBucket.getSourcePath() + uri);
    }

    public void mkcol() throws IOException {
        adapter.mkcol(fileBucket.getSourcePath() + uri);
    }

    public RequestStatus delete() throws IOException {
        RequestStatus status = new RequestStatus();

        if (!hasPath()) {
            status.setCode(HttpServletResponse.SC_NOT_FOUND);
            return status;
        } else {
            status.setSuccess(true);
        }
        adapter.delete(fileBucket.getSourcePath() + uri);
        return status;
    }

    public void put() throws IOException {
        adapter.put(fileBucket.getSourcePath() + uri);
    }

    public RequestStatus move() throws IOException {
        RequestStatus status = new RequestStatus();

        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        String destHeader = request.getHeader("Destination");

        if (destHeader == null || !destHeader.startsWith("http")) {
            status.setCode(HttpServletResponse.SC_BAD_REQUEST);
            return status;
        }

        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        if (!destPathRaw.startsWith(fileBucket.getPath())) {
            status.setCode(HttpServletResponse.SC_BAD_REQUEST);
            return status;
        }

        destPathRaw = destPathRaw.replaceFirst(fileBucket.getPath(), "");

        boolean overwrite = !"F".equalsIgnoreCase(request.getHeader("Overwrite"));

        if (adapter.hasPath(destPathRaw.toString())) {
            if (overwrite) {
                adapter.delete(destPathRaw.toString());
            } else {
                status.setCode(HttpServletResponse.SC_PRECONDITION_FAILED);
                return status;
            }
        }


        adapter.move(fileBucket.getSourcePath() + uri, fileBucket.getSourcePath() + destPathRaw);
        status.setCode(HttpServletResponse.SC_CREATED);
        status.setSuccess(true);
        return status;
    }

    public RequestStatus copy() throws IOException {
        RequestStatus status = new RequestStatus();


        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        String destHeader = request.getHeader("Destination");

        if (destHeader == null || !destHeader.startsWith("http")) {
            status.setCode(HttpServletResponse.SC_BAD_REQUEST);
            return status;
        }

        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        if (!destPathRaw.startsWith(fileBucket.getPath())) {
            status.setCode(HttpServletResponse.SC_BAD_REQUEST);
            return status;
        }


        destPathRaw = destPathRaw.replaceFirst(fileBucket.getPath(), "");

        boolean overwrite = !"F".equalsIgnoreCase(request.getHeader("Overwrite"));
        if (adapter.hasPath(destPathRaw) && !overwrite) {
            status.setCode(HttpServletResponse.SC_BAD_REQUEST);
            status.setMessage("Destination already exists！");
            return status;
        }

        adapter.copy(fileBucket.getSourcePath() + uri, fileBucket.getSourcePath() + destPathRaw);
        status.setCode(HttpServletResponse.SC_CREATED);
        status.setSuccess(true);
        return status;
    }
}
