package cn.joker.webdav.webdav.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.fileTask.taskImpl.CopyTask;
import cn.joker.webdav.fileTask.taskImpl.MoveTask;
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
import okhttp3.*;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.http.message.BasicHttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

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

    @Autowired
    private ISysSettingService sysSettingService;

    @Autowired
    private TaskManager taskManager;

    private Sardine getSardine(FileBucket fileBucket) {
        JSONObject jsonObject = fileBucket.getFieldJson();
        String url = jsonObject.getString("url") + ":" + jsonObject.getString("prot");
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
        List<FileResource> list = filePathCacheService.get(PathUtils.toLinuxPath(path.getParent()));
        if (list != null && !list.isEmpty()) {
            String name = path.getFileName().toString();
            for (FileResource resource : list) {
                if (name.equals(resource.getName())) {
                    return resource;
                }
            }
        }

        path = Paths.get(uri);
        list = propFind(fileBucket, PathUtils.toLinuxPath(path.getParent()), false);

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


        String ePath = Arrays.stream(uri.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        Sardine sardine = getSardine(fileBucket);
        List<DavResource> davResourceList = sardine.list(fileBucket.getFieldJson().getString("davUrl") + ePath);

        davResourceList.forEach(davResource -> {

            String path = davResource.getHref().getPath();

            path = path.replaceFirst(fileBucket.getSourcePath(), "/");

            path = PathUtils.normalizePath(fileBucket.getSourcePath() + path);

            if (path.equals("/") || path.equals(uri)) {
                return;
            }

            FileResource fileResource = new FileResource();
            fileResource.setName(davResource.getName());
            fileResource.setContentType(davResource.getContentType());
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
        Map<String, String> headerMap = new HashMap<>();
        String url = getDownloadUrl(fileBucket, path, headerMap);
        HttpServletRequest request = RequestHolder.getRequest();

        if (request != null && StringUtils.hasText(request.getHeader("range"))) {
            headerMap.put("Range", request.getHeader("range"));
        }

        HttpServletResponse response = RequestHolder.getResponse();

        Headers headers = Headers.of(headerMap);

        Request httpRequest = new Request.Builder()
                .headers(headers)
                .url(url)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response httpResponse = client.newCall(httpRequest).execute();

        if (!httpResponse.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        response.setStatus(httpResponse.code());

        response.setHeader("Content-Type", httpResponse.header("Content-Type"));
        if (httpResponse.header("Content-Range") != null) {
            response.setHeader("Content-Range", httpResponse.header("Content-Range"));
        }
        response.setHeader("Accept-Ranges", "bytes");
        if (httpResponse.header("Content-Length") != null) {
            response.setHeader("Content-Length", httpResponse.header("Content-Length"));
        }

        ResponseBody body = httpResponse.body();

        InputStream in = body.byteStream();
        try {
            in.transferTo(response.getOutputStream());
        } catch (ClientAbortException clientAbortException) {
            in.close();
        }
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {

    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        path = Arrays.stream(path.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        String url = fileBucket.getFieldJson().getString("davUrl") + path;

        Sardine sardine = getSardine(fileBucket);
        sardine.delete(url);
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        path = Arrays.stream(path.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        String url = fileBucket.getFieldJson().getString("davUrl") + path;

        Sardine sardine = getSardine(fileBucket);
        sardine.createDirectory(url);
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            fromPath = Arrays.stream(fromPath.split("/"))
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .collect(Collectors.joining("/"));

            String url = fromFileBucket.getFieldJson().getString("davUrl") + fromPath;

            toPath = Arrays.stream(toPath.split("/"))
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .collect(Collectors.joining("/"));

            Sardine sardine = getSardine(fromFileBucket);
            sardine.move(url, toPath);
        } else {
            //夸桶操作
            String uuid = UUID.randomUUID().toString().replace("-", "");
            MoveTask moveTask = new MoveTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

            taskManager.startTask(uuid, moveTask, StpUtil.getTokenValue());
        }
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            fromPath = Arrays.stream(fromPath.split("/"))
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .collect(Collectors.joining("/"));

            String url = fromFileBucket.getFieldJson().getString("davUrl") + fromPath;

            toPath = Arrays.stream(toPath.split("/"))
                    .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                    .collect(Collectors.joining("/"));

            Sardine sardine = getSardine(fromFileBucket);
            sardine.copy(url, toPath);
        } else {
            //夸桶操作
            String uuid = UUID.randomUUID().toString().replace("-", "");
            CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

            taskManager.startTask(uuid, copyTask, StpUtil.getTokenValue());
        }
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        String authBase64 = Base64.getEncoder()
                .encodeToString((fileBucket.getFieldJson().getString("username") + ":" + fileBucket.getFieldJson().getString("password")).getBytes(StandardCharsets.UTF_8));
        authBase64 = "Basic " + authBase64;
        header.put("Authorization", authBase64);

        path = PathUtils.normalizePath(path);

        path = Arrays.stream(path.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        return fileBucket.getFieldJson().getString("davUrl") + path;
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        Sardine sardine = getSardine(fileBucket);
        try {
            sardine.list(fileBucket.getFieldJson().getString("davUrl") + fileBucket.getSourcePath());
            return "working";
        } catch (IOException e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }
}
