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
import lombok.Getter;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class AdapterManager {

    private String uri;

    private CacheManager cacheManager;

    private IFileAdapter adapter;

    private FileBucket fileBucket;

    private List<FileBucket> fileBucketList = new ArrayList<>();

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public AdapterManager(String uri) {
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
        fileBucketList = FileBucketPathUtils.matchSelfAndAllDescendants(uri, list).getDirectChildren();

        if (fileBucket == null && !fileBucketList.isEmpty()) {
            fileBucket = fileBucketList.getFirst();
        }

        this.uri = uri.replaceFirst(fileBucket.getPath(), "");
        if (!this.uri.startsWith("/")) {
            this.uri = "/" + this.uri;
        }
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
            if (fileBucket.getPath().contains(this.uri)) {
                String updateTime = fileBucket.getUpdateTime();
                if (StringUtils.hasText(updateTime)) {
                    fileResource.setDate(format.parse(updateTime));
                }
            } else {
                for (FileBucket bucket : fileBucketList) {
                    if (bucket.getPath().contains(this.uri)) {
                        fileResource.setDate(format.parse(bucket.getUpdateTime()));

                        fileResource.setHref(this.uri);

                        String path = this.uri.substring(1, this.uri.length() - 1);
                        fileResource.setName(path);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!StringUtils.hasText(fileResource.getName())){
            fileResource.setName("");
        }

        if (fileResource.getDate() == null) {
            fileResource = adapter.getFolderItself(fileBucket, uri);
        } else {
            if (!StringUtils.hasText(fileResource.getHref())) {
                fileResource.setHref("/");
            }
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

        for (FileBucket bucket : fileBucketList) {
            FileResource resource = new FileResource();
            resource.setType("folder");

            try {
                resource.setDate(format.parse(bucket.getUpdateTime()));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
            String bucketPath = this.fileBucket.getPath();

            if ("/".equals(bucketPath)) {
                bucketPath = "";
            }

            String name = bucket.getPath().replaceFirst(bucketPath + "/", "");

            if (name.contains("/")) {
                name = "/" + name;
                name = name.replaceFirst(uri, "");
                if (name.startsWith("/")) {
                    name = name.replaceFirst("/", "");
                }
                String[] names = name.split("/");
                name = names[0];

                String[] paths = bucket.getPath().split(name);

                resource.setHref(paths[0] + name);
            } else {
                resource.setHref(bucket.getPath());
            }

            resource.setName(name);

            resource.setSize(0L);

            list.add(resource);
        }

        list.sort(new Comparator<FileResource>() {
            @Override
            public int compare(FileResource o1, FileResource o2) {
                int io1 = "folder".equals(o1.getType()) ? 1 : 2;
                int io2 = "folder".equals(o2.getType()) ? 1 : 2;
                if (io1 > io2) {
                    return 1;
                } else if (io1 < io2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        status.setFileResources(list);
        return status;
    }

    public GetFileResource get() throws IOException {
        return adapter.get(fileBucket.getSourcePath() + uri);
    }

    public boolean hasPath() {
        boolean has = adapter.hasPath(fileBucket.getSourcePath() + uri);
        if (!has) {
            for (FileBucket bucket : fileBucketList) {
                if (bucket.getPath().startsWith(uri)) {
                    has = true;
                    break;
                }
            }
        }
        return has;
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

    public String getDownloadUrl(String fileType) {
        return adapter.getDownloadUrl(fileBucket.getPath() + uri, fileType);
    }
}
