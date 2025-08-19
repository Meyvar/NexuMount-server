package cn.joker.webdav.webdav.adapter.contract;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.config.ExternalConfig;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.SprintContextUtil;
import cn.joker.webdav.webdav.adapter.trie.FileBucketPathUtils;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestStatus;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public class AdapterManager {

    @Getter
    private String uri;

    private CacheManager cacheManager;

    FilePathCacheService filePathCacheService;

    @Getter
    private IFileAdapter adapter;

    @Getter
    private FileBucket fileBucket;

    private List<FileBucket> fileBucketList = new ArrayList<>();

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private String userPath;

    public AdapterManager(String uri, String userPath) {
        this.userPath = userPath;
        if (uri.equals("/") && !this.userPath.isEmpty()) {
            uri = "";
        }
        uri = this.userPath + uri;
        cacheManager = SprintContextUtil.getApplicationContext().getBean("cacheManager", CacheManager.class);
        filePathCacheService = SprintContextUtil.getApplicationContext().getBean("filePathCacheService", FilePathCacheService.class);

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

        if (!StringUtils.hasText(fileResource.getName())) {
            fileResource.setName("");
        }

        if (fileResource.getDate() == null) {
            fileResource = adapter.getFolderItself(fileBucket, uri);
        } else {
            if (!StringUtils.hasText(fileResource.getHref())) {
                fileResource.setHref("/");
            }
        }
        fileResource.setHref(fileResource.getHref().replaceFirst(this.userPath, ""));
        return fileResource;
    }

    /**
     * ？获取文件列表
     *
     * @return
     */
    public RequestStatus propFind(boolean refresh) throws IOException {
        RequestStatus status = new RequestStatus();

        if (!hasPath()) {
            status.setCode(HttpServletResponse.SC_NOT_FOUND);
            return status;
        } else {
            status.setSuccess(true);
        }

        if ("rootAdapter".equals(fileBucket.getAdapter())) {
            status.setFileResources(adapter.propFind(fileBucket, uri, refresh));
            return status;
        }

        if (refresh) {
            filePathCacheService.remove(fileBucket.getPath() + uri);
        }

        List<FileResource> list = filePathCacheService.get(fileBucket.getPath() + uri);

        if (list != null && !list.isEmpty() && StringUtils.hasText(list.getFirst().getHref())) {
            for (FileResource resource : list) {
                resource.setHref(resource.getHref().replaceFirst(this.userPath, ""));
            }

            status.setFileResources(list);
            return status;
        }

        list = adapter.propFind(fileBucket, uri, refresh);

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

        filePathCacheService.put(fileBucket.getPath() + uri, list);

        for (FileResource resource : list) {
            resource.setHref(resource.getHref().replaceFirst(this.userPath, ""));
        }

        status.setFileResources(list);
        return status;
    }

    public void get() throws Exception {
        adapter.get(fileBucket, PathUtils.normalizePath(fileBucket.getSourcePath() + uri));
    }

    public boolean hasPath() {
        boolean has = adapter.hasPath(fileBucket, uri);
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
        adapter.mkcol(fileBucket, PathUtils.normalizePath(fileBucket.getSourcePath() + uri));
        String path = PathUtils.normalizePath(fileBucket.getPath() + uri);
        filePathCacheService.remove(PathUtils.toLinuxPath(Paths.get(path).getParent()));
    }

    public RequestStatus delete() throws IOException {
        RequestStatus status = new RequestStatus();

        if (!hasPath()) {
            status.setCode(HttpServletResponse.SC_NOT_FOUND);
            return status;
        } else {
            status.setSuccess(true);
        }
        adapter.delete(fileBucket, PathUtils.normalizePath(fileBucket.getSourcePath() + uri));

        String path = PathUtils.normalizePath(fileBucket.getPath() + uri);
        filePathCacheService.remove(Paths.get(path).getParent().toString());
        return status;
    }

    public void put(InputStream inputStream) throws Exception {
        HttpServletRequest req = RequestHolder.getRequest();
        String mTime = req.getHeader("X-Oc-Mtime");
        if (!StringUtils.hasText(mTime)) {
            mTime = req.getHeader("Last-Modified");
        }

        ExternalConfig externalConfig = SprintContextUtil.getBean("externalConfig", ExternalConfig.class);

        // 确保父目录存在
        Path targetPath = Paths.get(externalConfig.getTargetPath());
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }

        String filePath = targetPath + "/" + UUID.randomUUID() + new Date().getTime() + Paths.get(uri).getFileName();

        OutputStream out = Files.newOutputStream(Paths.get(filePath));
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }

        out.flush();
        inputStream.close();

        if (StringUtils.hasText(mTime)) {
            long timestamp = Long.parseLong(mTime);
            FileTime fileTime = FileTime.from(Instant.ofEpochSecond(timestamp));
            Files.setLastModifiedTime(targetPath, fileTime);
        }

        try {
            adapter.put(fileBucket, PathUtils.normalizePath(fileBucket.getSourcePath() + uri), Paths.get(filePath), null);
        } catch (Exception e) {
            throw e;
        } finally {
            String path = PathUtils.normalizePath(fileBucket.getPath() + uri);
            filePathCacheService.remove(PathUtils.toLinuxPath(Paths.get(path).getParent()));
            Paths.get(filePath).toFile().delete();
        }


    }

    public RequestStatus move(AdapterManager toAdapterManager) throws IOException {
        RequestStatus status = new RequestStatus();

        HttpServletRequest request = RequestHolder.getRequest();

        URI destUriObj = URI.create(toAdapterManager.uri);
        String destPathRaw = destUriObj.getPath();

        boolean overwrite = !"F".equalsIgnoreCase(request.getHeader("Overwrite"));

        if (toAdapterManager.adapter.hasPath(toAdapterManager.fileBucket, destPathRaw)) {
            if (overwrite) {
                toAdapterManager.adapter.delete(toAdapterManager.fileBucket, destPathRaw);
            } else {
                status.setCode(HttpServletResponse.SC_PRECONDITION_FAILED);
                return status;
            }
        }


        adapter.move(fileBucket, PathUtils.normalizePath(fileBucket.getSourcePath() + uri), toAdapterManager.fileBucket, PathUtils.normalizePath(toAdapterManager.fileBucket.getSourcePath() + destPathRaw));

        String path = PathUtils.normalizePath(fileBucket.getPath() + uri);
        filePathCacheService.remove(PathUtils.toLinuxPath(Paths.get(path).getParent()));
        path = PathUtils.normalizePath(toAdapterManager.fileBucket.getPath() + destPathRaw);
        filePathCacheService.remove(PathUtils.toLinuxPath(Paths.get(path).getParent()));


        status.setCode(HttpServletResponse.SC_CREATED);
        status.setSuccess(true);
        return status;
    }

    public RequestStatus copy(AdapterManager toAdapterManager) throws IOException {
        RequestStatus status = new RequestStatus();

        HttpServletRequest request = RequestHolder.getRequest();

        URI destUriObj = URI.create(URLEncoder.encode(toAdapterManager.uri, StandardCharsets.UTF_8));
        String destPathRaw = destUriObj.getPath();

        boolean overwrite = request == null || !"F".equalsIgnoreCase(request.getHeader("Overwrite"));
        if (toAdapterManager.adapter.hasPath(toAdapterManager.fileBucket, destPathRaw) && !overwrite) {
            status.setCode(HttpServletResponse.SC_BAD_REQUEST);
            status.setMessage("Destination already exists！");
            return status;
        }

        adapter.copy(fileBucket, PathUtils.normalizePath(fileBucket.getSourcePath() + uri), toAdapterManager.fileBucket, PathUtils.normalizePath(toAdapterManager.fileBucket.getSourcePath() + destPathRaw));

        String path = PathUtils.normalizePath(fileBucket.getPath() + uri);
        filePathCacheService.remove(PathUtils.toLinuxPath(Paths.get(path).getParent()));
        path = PathUtils.normalizePath(toAdapterManager.fileBucket.getPath() + destPathRaw);
        filePathCacheService.remove(PathUtils.toLinuxPath(Paths.get(path).getParent()));

        status.setCode(HttpServletResponse.SC_CREATED);
        status.setSuccess(true);
        return status;
    }

    public String getDownloadUrl() throws IOException {
        String path = PathUtils.normalizePath(fileBucket.getPath() + uri);
        path = path.replaceFirst(this.userPath, "");
        return adapter.getDownloadUrl(fileBucket, path);
    }
}
