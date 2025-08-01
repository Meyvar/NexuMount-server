package cn.joker.webdav.webdav.adapter;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

@AdapterComponent(title = "中国移动云盘")
public class ChinaMobileCloudAdapter implements IFileAdapter {

    @Autowired
    private CacheManager cacheManager;

    @Getter
    @Setter
    @ParamAnnotation(label = "authorization")
    private String authorization;

    private String BASIC_URL = "https://personal-kd-njs.yun.139.com/hcy";

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        if (path.equals("/")) {
            return true;
        } else {
            String filePath = path;
            if (!fileBucket.getSourcePath().equals("/")) {
                filePath = fileBucket.getSourcePath() + path;
            }

            return StringUtils.hasText(queryId(filePath, fileBucket.getFieldJson().getString("authorization")));
        }
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        String path = uri;
        if (!fileBucket.getSourcePath().equals("/")) {
            path = fileBucket.getSourcePath() + path;
        }

        Path pathObj = Paths.get(path).normalize();
        Path parent = pathObj.getParent();

        String id = "";
        if (parent.toString().equals("/")) {
            id = "/";
        } else {
            id = queryId(parent.toString(), fileBucket.getFieldJson().getString("authorization"));
        }

        List<FileResource> list = list(id, fileBucket.getFieldJson().getString("authorization"));

        FileResource fileResource = new FileResource();

        for (FileResource resource : list) {
            if (resource.getName().equals(pathObj.getFileName().toString())) {
                fileResource = resource;
                break;
            }
        }

        fileResource.setContentType(fileBucket.getFieldJson().getString("contentType"));
        if (!uri.endsWith("/") && fileResource.getType().equals("folder")) {
            uri += "/";
        }
        fileResource.setHref(fileBucket.getPath() + uri);
        if ("file".equals(fileResource.getType())) {
            fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));
        }
        return fileResource;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        String path = uri;
        if (!fileBucket.getSourcePath().equals("/")) {
            path = fileBucket.getSourcePath() + path;
        }

        String id = "/";

        if (!path.equals("/")) {
            id = queryId(path, fileBucket.getFieldJson().getString("authorization"));
        }

        if (refresh) {
            cleanCache(fileBucket.getFieldJson().getString("authorization"), id);
        }

        List<FileResource> list = list(id, fileBucket.getFieldJson().getString("authorization"));


        for (FileResource fileResource : list) {
            if (!uri.endsWith("/")) {
                uri += "/";
            }
            fileResource.setHref(fileBucket.getPath() + uri + fileResource.getName());
        }

        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        FileResource fileResource = getFolderItself(fileBucket, path);
        String downloadUrl = getDownloadUrl(fileBucket, path, fileResource.getContentType());
        RequestHolder.getResponse().sendRedirect(downloadUrl);
    }

    @Override
    public void put(FileBucket fileBucket, String path, InputStream input) throws Exception {
        StreamResult streamResult = processStream(input);

        Path filePath = Paths.get(path);

        String id = queryId(filePath.getParent().toString(), fileBucket.getFieldJson().getString("authorization"));

        if (!StringUtils.hasText(id) && !hasPath(fileBucket, filePath.getParent().toString())) {
            synchronized (this) {
                String[] parts = filePath.getParent().toString().split("/");
                StringBuilder current = new StringBuilder();

                for (String part : parts) {
                    if (part.isEmpty()) continue;
                    current.append("/").append(part);

                    if (!hasPath(fileBucket, current.toString())) {
                        mkcol(fileBucket, current.toString());
                    }
                }

                id = queryId(filePath.getParent().toString(), fileBucket.getFieldJson().getString("authorization"));
            }
        }

        cleanCache(fileBucket.getFieldJson().getString("authorization"), id);

        Long size = streamResult.size;


        JSONObject jsonObject = JSONObject.parseObject("{\"parentFileId\":\"1234\",\"name\":\"3.png\",\"type\":\"file\",\"size\":456136,\"fileRenameMode\":\"auto_rename\",\"contentHash\":\"1234\",\"contentHashAlgorithm\":\"SHA256\",\"contentType\":\"application/oct-stream\",\"parallelUpload\":false,\"partInfos\":[{\"parallelHashCtx\":{\"partOffset\":0},\"partNumber\":1,\"partSize\":456136}]}");
        jsonObject.put("contentHash", streamResult.sha256);
        jsonObject.put("name", filePath.getFileName().toString());
        jsonObject.put("size", size);
        jsonObject.getJSONArray("partInfos").getJSONObject(0).put("partSize", size);
        jsonObject.put("parentFileId", id);


        String url = BASIC_URL + "/file/create";

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                .body(jsonObject.toJSONString())
                .execute();

        if (response.isOk()) {
            jsonObject = JSONObject.parseObject(response.body());
            if (jsonObject.getBoolean("success")) {
                jsonObject = jsonObject.getJSONObject("data");
                if (!jsonObject.getBoolean("rapidUpload")) {
                    jsonObject = jsonObject.getJSONArray("partInfos").getJSONObject(0);
                    url = jsonObject.getString("uploadUrl");


                    response = HttpRequest.put(url)
                            .header("Content-Type", "application/octet-stream")
                            .body(IoUtil.readBytes(streamResult.stream))
                            .execute();
                    if (!response.isOk()) {
                        throw new RuntimeException("status is " + response.getStatus());
                    }
                }
            } else {
                throw new Exception(jsonObject.getString("message"));
            }
        } else {
            throw new RuntimeException("status is " + response.getStatus());
        }
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        Path queryPath = Paths.get(path);
        String id = queryId(queryPath.toString(), fileBucket.getFieldJson().getString("authorization"));

        cleanCache(fileBucket.getFieldJson().getString("authorization"), queryId(queryPath.getParent().toString(), fileBucket.getFieldJson().getString("authorization")));

        String url = BASIC_URL + "/recyclebin/batchTrash";
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileIds", Collections.singletonList(id));

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                .body(jsonObject.toJSONString())
                .execute();

        if (response.isOk()) {
            jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getBoolean("success")) {
                throw new RuntimeException(jsonObject.getString("message"));
            }
        } else {
            throw new RuntimeException("status is " + response.getStatus());
        }

        taskGet(jsonObject.getJSONObject("data").getString("taskId"), fileBucket.getFieldJson().getString("authorization"));
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        Path queryPath = Paths.get(path);

        String id = "";

        if (queryPath.getParent().toString().equals("/")) {
            id = "/";
        } else {
            id = queryId(queryPath.getParent().toString(), fileBucket.getFieldJson().getString("authorization"));
        }

        cleanCache(fileBucket.getFieldJson().getString("authorization"), id);

        String url = BASIC_URL + "/file/create";
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("parentFileId", id);
        jsonObject.put("type", "folder");
        jsonObject.put("fileRenameMode", "force_rename");
        jsonObject.put("description", "");
        jsonObject.put("name", queryPath.getFileName().toString());

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                .body(jsonObject.toJSONString())
                .execute();

        if (response.isOk()) {
            jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getBoolean("success")) {
                throw new RuntimeException(jsonObject.getString("message"));
            }
        } else {
            throw new RuntimeException("status is:" + response.getStatus());
        }
    }

    @Override
    public void move(FileBucket fileBucket, String sourcePath, String destPath) throws IOException {
        Path oldPath = Paths.get(sourcePath);
        Path newPath = Paths.get(destPath);


        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileIds", Collections.singletonList(queryId(oldPath.toString(), fileBucket.getFieldJson().getString("authorization"))));
        jsonObject.put("toParentFileId", queryId(newPath.getParent().toString(), fileBucket.getFieldJson().getString("authorization")));

        cleanCache(fileBucket.getFieldJson().getString("authorization"), jsonObject.getString("toParentFileId"));
        cleanCache(fileBucket.getFieldJson().getString("authorization"), queryId(oldPath.getParent().toString(), fileBucket.getFieldJson().getString("authorization")));

        String url = BASIC_URL + "/file/batchMove";

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                .body(jsonObject.toJSONString())
                .execute();

        if (response.isOk()) {
            jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getBoolean("success")) {
                throw new RuntimeException(jsonObject.getString("message"));
            }
        } else {
            throw new RuntimeException("status is " + response.getStatus());
        }

        taskGet(jsonObject.getJSONObject("data").getString("taskId"), fileBucket.getFieldJson().getString("authorization"));
    }

    @Override
    public void copy(FileBucket fileBucket, String sourcePath, String destPath) throws IOException {
        Path oldPath = Paths.get(sourcePath);
        Path newPath = Paths.get(destPath);


        JSONObject jsonObject = new JSONObject();
        jsonObject.put("fileIds", Collections.singletonList(queryId(oldPath.toString(), fileBucket.getFieldJson().getString("authorization"))));
        jsonObject.put("toParentFileId", queryId(newPath.getParent().toString(), fileBucket.getFieldJson().getString("authorization")));

        cleanCache(fileBucket.getFieldJson().getString("authorization"), jsonObject.getString("toParentFileId"));
        cleanCache(fileBucket.getFieldJson().getString("authorization"), queryId(oldPath.getParent().toString(), fileBucket.getFieldJson().getString("authorization")));

        String url = BASIC_URL + "/file/batchCopy";

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                .body(jsonObject.toJSONString())
                .execute();

        if (response.isOk()) {
            jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getBoolean("success")) {
                throw new RuntimeException(jsonObject.getString("message"));
            }
        } else {
            throw new RuntimeException("status is " + response.getStatus());
        }

        taskGet(jsonObject.getJSONObject("data").getString("taskId"), fileBucket.getFieldJson().getString("authorization"));
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, String fileType) throws IOException {

        JSONObject jsonObject = new JSONObject();

        FileResource fileResource = getFolderItself(fileBucket, path);

        if (fileResource.getType().equals("folder")) {
            String url = BASIC_URL + "/file/archiveFiles";
            jsonObject.put("fileIds", Collections.singletonList(fileResource.getId()));
            jsonObject.put("name", fileResource.getName());

            HttpResponse response = HttpRequest.post(url)
                    .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                    .body(jsonObject.toJSONString())
                    .execute();

            if (response.isOk()) {
                jsonObject = JSONObject.parseObject(response.body());
                if (jsonObject.getBoolean("success")) {
                    String downloadUrl = jsonObject.getJSONObject("data").getString("downloadUrl");
                    if (StringUtils.hasText(downloadUrl)) {
                        return downloadUrl;
                    }

                    jsonObject = taskGet(jsonObject.getJSONObject("data").getString("taskId"),  fileBucket.getFieldJson().getString("authorization"));

                    String extraData = jsonObject.getString("extraData");
                    jsonObject = JSONObject.parseObject(extraData);

                    return jsonObject.getString("url");
                } else {
                    throw new RuntimeException(jsonObject.getString("message"));
                }
            } else {
                throw new RuntimeException("status is " + response.getStatus());
            }
        } else {
            String url = BASIC_URL + "/file/getDownloadUrl";
            jsonObject.put("fileId", fileResource.getId());

            HttpResponse response = HttpRequest.post(url)
                    .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                    .body(jsonObject.toJSONString())
                    .execute();

            if (response.isOk()) {
                jsonObject = JSONObject.parseObject(response.body());
                if (jsonObject.getBoolean("success")) {
                    return jsonObject.getJSONObject("data").getString("url");
                } else {
                    throw new RuntimeException(jsonObject.getString("message"));
                }
            } else {
                throw new RuntimeException("status is " + response.getStatus());
            }
        }
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        String url = BASIC_URL + "/file/list";
        JSONObject jsonObject = JSONObject.parseObject("{\"pageInfo\":{\"pageSize\":100,\"pageCursor\":null},\"orderBy\":\"updated_at\",\"orderDirection\":\"DESC\",\"parentFileId\":\"/\",\"imageThumbnailStyleList\":[\"Small\",\"Large\"]}");
        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(fileBucket.getFieldJson().getString("authorization")))
                .body(jsonObject.toJSONString())
                .execute();
        if (response.isOk()) {
            jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getBoolean("success")) {
                return jsonObject.getString("message");
            } else {
                return "work";
            }
        } else {
            return "status is:" + response.getStatus();
        }
    }

    private String queryId(String uri, String authorization) {
        String[] paths = uri.split("/");

        String search = paths[paths.length - 1];

        List<FileResource> fileResourceList = new ArrayList<>();

        for (int i = 0; i < paths.length - 1; i++) {
            if (i == 0) {
                fileResourceList = list("/", authorization);
                continue;
            }

            for (FileResource fileResource : fileResourceList) {
                if (fileResource.getName().equals(paths[i])) {
                    fileResourceList = list(fileResource.getId(), authorization);
                    break;
                }
            }
        }

        for (FileResource fileResource : fileResourceList) {
            if (fileResource.getName().equals(search)) {
                return fileResource.getId();
            }
        }
        return "";
    }

    private void cleanCache(String authorization, String id) {
        Cache cache = cacheManager.getCache("fileResourceMap");
        Map<String, List<FileResource>> map = cache.get("chinaMobileCloudAdapter:" + authorization, Map.class);
        if (map != null) {
            map.remove(id);
        }
    }

    private List<FileResource> list(String id, String authorization) {


        Cache cache = cacheManager.getCache("fileResourceMap");

        Map<String, List<FileResource>> map = cache.get("chinaMobileCloudAdapter:" + authorization, Map.class);

        if (map == null) {
            map = new HashMap<>();
        }

        List<FileResource> list = map.get(id);

        if (list != null && !list.isEmpty()) {
            return new ArrayList<>(list);
        }

        String url = BASIC_URL + "/file/list";

        JSONObject jsonObject = JSONObject.parseObject("{\"pageInfo\":{\"pageSize\":100,\"pageCursor\":null},\"orderBy\":\"updated_at\",\"orderDirection\":\"DESC\",\"parentFileId\":\"/\",\"imageThumbnailStyleList\":[\"Small\",\"Large\"]}");

        jsonObject.put("parentFileId", id);

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader(authorization))
                .body(jsonObject.toJSONString())
                .execute();

        if (response.isOk()) {
            jsonObject = JSON.parseObject(response.body());

            if (!jsonObject.getBoolean("success")) {
                throw new RuntimeException(jsonObject.getString("message"));
            }


            JSONArray itemArr = jsonObject.getJSONObject("data").getJSONArray("items");

            if (itemArr.size() == 0) {
                return new ArrayList<>();
            }

            List<FileResource> fileResourceList = new LinkedList<>();

            for (int i = 0; i < itemArr.size(); i++) {
                JSONObject item = itemArr.getJSONObject(i);
                FileResource fileResource = new FileResource();

                fileResource.setId(item.getString("fileId"));
                fileResource.setName(item.getString("name"));
                fileResource.setType(item.getString("type"));
                fileResource.setSize(item.getString("type").equals("folder") ? 0L : item.getLong("size"));
                fileResource.setDate(DateUtil.parse(item.getString("updatedAt"), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
                fileResource.setContentType(URLConnection.guessContentTypeFromName(item.getString("name")));


                fileResourceList.add(fileResource);
            }

            map.put(id, new ArrayList<>(fileResourceList));
            cache.put("chinaMobileCloudAdapter:" + authorization, map);

            return fileResourceList;
        } else {
            throw new RuntimeException("status is " + response.getStatus());
        }
    }


    private Map<String, String> getHeader(String authorization) {
        Map<String, String> map = new HashMap<>();
        map.put("Authorization", authorization);
        map.put("X-Yun-Api-Version", "v1");
        map.put("X-Yun-app-channel", "10000034");
        map.put("x-yun-client-info", "||9|7.15.4|chrome|138.0.0.0|||macos 10.15.7||zh-CN|||dW5kZWZpbmVk||");
        map.put("Content-Type", "application/json");
        return map;
    }


    static class StreamResult {
        public String sha256;
        public long size;
        public InputStream stream;

        public StreamResult(String sha256, long size, InputStream stream) {
            this.sha256 = sha256;
            this.size = size;
            this.stream = stream;
        }
    }

    private StreamResult processStream(InputStream inputStream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] temp = new byte[8192];
        int read;
        long size = 0;

        while ((read = inputStream.read(temp)) != -1) {
            digest.update(temp, 0, read);
            buffer.write(temp, 0, read);
            size += read;
        }

        byte[] hashBytes = digest.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }

        return new StreamResult(hex.toString(), size, new ByteArrayInputStream(buffer.toByteArray()));
    }

    private JSONObject taskGet(String taskId, String authorization) {
        while (true) {
            String url = BASIC_URL + "/task/get";
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("taskId", taskId);

            HttpResponse response = HttpRequest.post(url)
                    .addHeaders(getHeader(authorization))
                    .body(jsonObject.toJSONString())
                    .execute();

            if (response.isOk()) {
                jsonObject = JSONObject.parseObject(response.body());
                if (jsonObject.getBoolean("success")) {

                    if (jsonObject.getJSONObject("data").getJSONObject("taskInfo").getString("status").equals("Succeed")) {
                        return jsonObject.getJSONObject("data");
                    } else {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }


                } else {
                    throw new RuntimeException(jsonObject.getString("message"));
                }
            } else {
                throw new RuntimeException("get task failed");
            }
        }
    }
}
