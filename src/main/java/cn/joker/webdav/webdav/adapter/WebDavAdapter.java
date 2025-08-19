package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSONObject;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AdapterComponent(title = "WebDav")
public class WebDavAdapter implements IFileAdapter {


    @Getter
    @Setter
    @ParamAnnotation(label = "服务器地址")
    private String url;

    @Getter
    @Setter
    @ParamAnnotation(label = "服务器端口号")
    private String prot;


    @Getter
    @Setter
    @ParamAnnotation(label = "用户名")
    private String username;

    @Getter
    @Setter
    @ParamAnnotation(label = "密码")
    private String password;

    @Autowired
    private CacheManager cacheManager;

    private Sardine getSardine(FileBucket fileBucket) {
        JSONObject jsonObject = fileBucket.getFieldJson();
        String url = jsonObject.getString("url") + ":" + jsonObject.getString("prot") + fileBucket.getSourcePath();
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        jsonObject.put("davUrl", url);
        Sardine sardine = SardineFactory.begin(jsonObject.getString("username"), jsonObject.getString("password"));
        return sardine;
    }


    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        Sardine sardine = getSardine(fileBucket);
        try {
            List<DavResource> resources = sardine.list(fileBucket.getFieldJson().getString("davUrl") + path, 0);
            return resources != null && !resources.isEmpty();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        return null;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        Cache cache = cacheManager.getCache("fileResourceMap");

        Map<String, List<FileResource>> map = cache.get("webDavAdapter:" + fileBucket.getPath(), Map.class);

        if (map == null) {
            map = new HashMap<>();
        }


        Sardine sardine = getSardine(fileBucket);
        List<DavResource> davResourceList = sardine.list(fileBucket.getFieldJson().getString("davUrl") + uri);

        List<FileResource> list = new ArrayList<>();

        davResourceList.forEach(davResource -> {

            if (davResource.getHref().getPath().equals("/") || davResource.getHref().getPath().equals(uri + "/")){
                return;
            }

            FileResource fileResource = new FileResource();
            fileResource.setName(davResource.getName());
            fileResource.setContentType(davResource.getContentType());
            fileResource.setHref(PathUtils.normalizePath(fileBucket.getPath() + davResource.getHref().getPath()));
            fileResource.setType(davResource.isDirectory() ? "folder" : "file");
            fileResource.setSize(davResource.getContentLength());
            fileResource.setDate(davResource.getModified());

            list.add(fileResource);
        });

        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {

    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {

    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {

    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {

    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {

    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {

    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path) throws IOException {
        return "";
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        return "";
    }
}
