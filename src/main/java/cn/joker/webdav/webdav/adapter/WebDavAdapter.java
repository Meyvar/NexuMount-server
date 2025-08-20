package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSONObject;
import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.io.ContentLengthInputStream;
import com.github.sardine.impl.io.HttpMethodReleaseInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import org.apache.http.message.BasicHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
    private FilePathCacheService filePathCacheService;

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
        try {

            List<FileResource> list = propFind(fileBucket, path, false);

            if (list == null || list.isEmpty()) {
                return false;
            }
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        Path path = Paths.get(PathUtils.normalizePath(fileBucket.getPath() + uri));
        List<FileResource> list = filePathCacheService.get(path.getParent().toString());
        if (list != null && !list.isEmpty()) {
            String name = path.getFileName().toString();
            for (FileResource resource : list) {
                if (name.equals(resource.getName())) {
                    return resource;
                }
            }
        }

        path = Paths.get(uri);
        list = propFind(fileBucket, path.getParent().toString(), false);

        for (FileResource resource : list) {
            if (resource.getName().equals(path.getFileName().toString())) {
                return resource;
            }
        }
        return null;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {

        List<FileResource> list = filePathCacheService.get(fileBucket.getPath() + uri);

        if (list != null && !list.isEmpty()) {
            return list;
        }

        Sardine sardine = getSardine(fileBucket);
        List<DavResource> davResourceList = sardine.list(fileBucket.getFieldJson().getString("davUrl") + uri);

        davResourceList.forEach(davResource -> {

            if (davResource.getHref().getPath().equals("/") || davResource.getHref().getPath().equals(uri + "/")) {
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

        filePathCacheService.put(fileBucket.getPath() + uri, list);


        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        String url = fileBucket.getFieldJson().getString("davUrl") + path;
        HttpServletRequest request = RequestHolder.getRequest();

        Map<String, String> headers = new HashMap<>();

        if (request != null && StringUtils.hasText(request.getHeader("range"))) {
            headers.put("Range", request.getHeader("range"));
        }

        Sardine sardine = getSardine(fileBucket);
        InputStream in = sardine.get(url, headers);


        HttpServletResponse response = RequestHolder.getResponse();

        OutputStream out = response.getOutputStream();



        in.transferTo(out);
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
