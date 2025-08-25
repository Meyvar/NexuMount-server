package cn.joker.webdav.webdav.adapter;

import cn.hutool.core.net.URLEncoder;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.TaskMeta;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.fileUpload.ProgressRequestBody;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@AdapterComponent(title = "百度网盘")
public class BaiduAdapter implements IFileAdapter {

    @Getter
    @Setter
    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    @Autowired
    private FilePathCacheService filePathCacheService;

    private final static String BASE_URL = "https://pan.baidu.com";

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        if (path.equals("/")) {
            return true;
        }
        try {
            getFolderItself(fileBucket, path);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        String name = Paths.get(uri).getFileName().toString();

        List<FileResource> list = propFind(fileBucket, PathUtils.toLinuxPath(Paths.get(uri).getParent()), false);

        for (FileResource resource : list) {
            if (name.equals(resource.getName())) {
                return resource;
            }
        }
        return null;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {

        List<FileResource> list = filePathCacheService.get(fileBucket.getUuid() + fileBucket.getPath() + uri);

        if (list != null && !list.isEmpty()) {
            return list;
        }

        if (list == null) {
            list = new ArrayList<>();
        }

        uri = URLEncoder.createQuery().encode(uri, StandardCharsets.UTF_8);


        int start = 0;
        do {
            String url = BASE_URL + "/rest/2.0/xpan/file?method=list&limit=1000&start=" + start + "&access_token=" + fileBucket.getFieldJson().getString("accessToken") + "&dir=" + uri;

            HttpResponse response = HttpRequest.get(url)
                    .execute();

            JSONObject jsonObject = JSONObject.parseObject(response.body());

            if (jsonObject.getInteger("errno") != 0) {
                throw new RuntimeException("资源获取失败");
            }

            JSONArray jsonArray = jsonObject.getJSONArray("list");
            for (int i = 0; i < jsonArray.size(); i++) {
                FileResource fileResource = new FileResource();
                jsonObject = jsonArray.getJSONObject(i);

                fileResource.setName(jsonObject.getString("server_filename"));
                fileResource.setDate(new Date(jsonObject.getLong("local_mtime") * 1000));
                fileResource.setSize(jsonObject.getLong("size"));
                fileResource.setId(jsonObject.getString("fs_id"));

                if (jsonObject.getInteger("isdir") == 1) {
                    fileResource.setType("folder");
                } else {
                    fileResource.setType("file");
                    fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));
                }

                list.add(fileResource);
            }
            start += 1000;
        } while (list.size() >= start);

        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);
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
        TaskMeta meta;
        if (hook != null) {
            meta = hook.getTaskMeta();
        } else {
            meta = null;
        }


        JSONObject jsonObject = getUserData(fileBucket.getFieldJson().getString("accessToken"));

        if (jsonObject.getInteger("errno") != 0) {
            throw new RuntimeException("获取用户信息失败！");
        }

        long fileSize = tempFilePath.toFile().length();

        long fragmentsSize = 1024L * 1024;

        String userVipType = jsonObject.getString("vip_type");

        switch (userVipType) {
            case "0":
                if (fileSize > 1024L * 1024 * 1024 * 4) {
                    throw new RuntimeException("百度网盘普通用户限制最大上传文件4GB");
                }
                fragmentsSize = fragmentsSize * 4;
                break;
            case "1":
                if (fileSize > 1024L * 1024 * 1024 * 10) {
                    throw new RuntimeException("百度网盘普通VIP限制最大上传文件10GB");
                }
                fragmentsSize = fragmentsSize * 16;
                break;
            case "2":
                if (fileSize > 1024L * 1024 * 1024 * 20) {
                    throw new RuntimeException("百度网盘SVIP限制最大上传文件20GB");
                }
                fragmentsSize = fragmentsSize * 32;
                break;
        }

        String destDir = tempFilePath.getParent() + "/fragments-" + fileBucket.getAdapter() + "-" + tempFilePath.getFileName();

        Files.createDirectories(Paths.get(destDir));

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(tempFilePath.toFile()))) {
            byte[] buffer = new byte[1024 * 1024]; // 1MB缓冲区
            int bytesRead;
            int partNumber = 1;
            long currentSize = 0;

            File currentPartFile = new File(destDir, String.valueOf(partNumber));
            FileOutputStream fos = new FileOutputStream(currentPartFile);

            while ((bytesRead = bis.read(buffer)) != -1) {

                if (currentSize + bytesRead > fragmentsSize) {
                    // 当前分片已满，写入剩余部分到新文件
                    int remain = (int) (fragmentsSize - currentSize);
                    fos.write(buffer, 0, remain);
                    fos.close();

                    partNumber++;
                    currentPartFile = new File(destDir, String.valueOf(partNumber));
                    fos = new FileOutputStream(currentPartFile);

                    // 写入剩余到新分片
                    fos.write(buffer, remain, bytesRead - remain);
                    currentSize = bytesRead - remain;
                } else {
                    fos.write(buffer, 0, bytesRead);
                    currentSize += bytesRead;
                }
            }

            fos.close();
        }

        List<File> fragments = new ArrayList<>();

        Files.list(Paths.get(destDir)).forEach(file -> {
            if (file.toFile().isFile()) {
                fragments.add(file.toFile());
            }
        });

        fragments.sort(new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                int f1 = Integer.parseInt(o1.getName());
                int f2 = Integer.parseInt(o2.getName());
                if (f1 > f2) {
                    return 1;
                } else if (f1 < f2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });

        List<String> fragmentsMd5List = new ArrayList<>();

        fragments.forEach(fragment -> {
            fragmentsMd5List.add(DigestUtil.md5Hex(fragment));
        });


        String createUrl = BASE_URL + "/rest/2.0/xpan/file?method=precreate&access_token=" + fileBucket.getFieldJson().getString("accessToken");

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("path", path)
                .add("size", String.valueOf(fileSize))
                .add("isdir", "0")
                .add("autoinit", "1")
                .add("rtype", "1")
                .add("block_list", JSONObject.toJSONString(fragmentsMd5List))
                .add("content-md5", DigestUtil.md5Hex(tempFilePath.toFile()))
                .add("local_ctime", String.valueOf(tempFilePath.toFile().lastModified()))
                .add("local_mtime", String.valueOf(tempFilePath.toFile().lastModified()))
                .build();

        Request request = new Request.Builder()
                .url(createUrl)
                .post(formBody)
                .build();

        Response response = client.newCall(request).execute();

        jsonObject = JSONObject.parseObject(response.body().string());

        if (jsonObject.getInteger("errno") != 0) {
            throw new RuntimeException("文件预上传失败！");
        }

        String uploadid = jsonObject.getString("uploadid");

        JSONArray blockList = jsonObject.getJSONArray("block_list");

        if (!blockList.isEmpty()) {
            createUrl = "https://d.pcs.baidu.com/rest/2.0/pcs/file?method=locateupload&appid=250528&access_token=" + fileBucket.getFieldJson().getString("accessToken")
                    + "&path=" + path
                    + "&uploadid=" + uploadid
                    + "&upload_version=2.0";

            String body = HttpUtil.get(createUrl);

            jsonObject = JSONObject.parseObject(body);

            if (jsonObject.getInteger("error_code") != 0) {
                throw new RuntimeException(jsonObject.getString("error_msg"));
            }

            JSONArray servers = jsonObject.getJSONArray("servers");

            String uploadBaseApi = servers.getJSONObject(0).getString("server");
            int uploadBaseApiIndex = 0;

            long nowNs = System.nanoTime();

            int uploadIndex = 0;
            while (uploadIndex < blockList.size()) {
                String uploadUrl = uploadBaseApi + "/rest/2.0/pcs/superfile2?method=upload&type=tmpfile&access_token=" + fileBucket.getFieldJson().getString("accessToken")
                        + "&path=" + URLEncoder.createQuery().encode(path, StandardCharsets.UTF_8)
                        + "&uploadid=" + uploadid
                        + "&partseq=" + blockList.getString(uploadIndex);


                RequestBody uploadBody = new ProgressRequestBody(fragments.get(blockList.getInteger(uploadIndex)), blockList.size() * fragmentsSize, (uploadIndex + 1) * fragmentsSize, nowNs, hook);

                MultipartBody multipartBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", blockList.getString(uploadIndex), uploadBody)
                        .build();

                Request uploadRequest = new Request.Builder()
                        .url(uploadUrl)
                        .post(multipartBody)
                        .build();

                Response uploadResponse = client.newCall(uploadRequest).execute();

                if (uploadResponse.code() == 403) {
                    uploadBaseApi = servers.getJSONObject(++uploadBaseApiIndex).getString("server");
                    continue;
                }

                jsonObject = JSONObject.parseObject(uploadResponse.body().string());

                if (jsonObject.getInteger("error_code") != null) {
                    throw new RuntimeException(jsonObject.getString("error_msg"));
                }
                uploadIndex++;
            }
        }


        createUrl = BASE_URL + "/rest/2.0/xpan/file?method=create&access_token=" + fileBucket.getFieldJson().getString("accessToken");

        formBody = new FormBody.Builder()
                .add("path", path)
                .add("size", String.valueOf(fileSize))
                .add("isdir", "0")
                .add("autoinit", "1")
                .add("uploadid", uploadid)
                .add("rtype", "1")
                .add("block_list", JSONObject.toJSONString(fragmentsMd5List))
                .add("content-md5", DigestUtil.md5Hex(tempFilePath.toFile()))
                .add("local_ctime", String.valueOf(tempFilePath.toFile().lastModified()))
                .add("local_mtime", String.valueOf(tempFilePath.toFile().lastModified()))
                .build();

        request = new Request.Builder()
                .url(createUrl)
                .post(formBody)
                .build();

        response = client.newCall(request).execute();

        jsonObject = JSONObject.parseObject(response.body().string());

        if (jsonObject.getInteger("errno") != 0) {
            throw new RuntimeException("文件合并失败！" + jsonObject.getInteger("errno"));
        }
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        List<String> filePath = List.of(path);

        JSONObject jsonObject = filemanager(JSONObject.toJSONString(filePath), fileBucket.getFieldJson().getString("accessToken"), "delete");
        if (jsonObject.getInteger("errno") != 0) {
            throw new RuntimeException("文件删除失败！");
        }
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        String url = BASE_URL + "/rest/2.0/xpan/file?method=create&access_token=" + fileBucket.getFieldJson().getString("accessToken");

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("path", path)
                .add("isdir", "1")
                .add("rtype", "1")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        Response response = client.newCall(request).execute();

        JSONObject jsonObject = JSONObject.parseObject(response.body().string());

        if (jsonObject.getInteger("errno") != 0) {
            throw new RuntimeException("文件夹创建失败！");
        }

    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("path", fromPath);

            Path path = Paths.get(toPath);

            jsonObject.put("dest", PathUtils.toLinuxPath(path.getParent()));
            jsonObject.put("newname", path.getFileName());

            JSONArray jsonArray = new JSONArray();
            jsonArray.add(jsonObject);

            jsonObject = filemanager(jsonArray.toJSONString(), fromFileBucket.getFieldJson().getString("accessToken"), "move");

            if (jsonObject.getInteger("errno") != 0) {
                throw new RuntimeException("文件移动失败！");
            }
        } else {

        }
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("path", fromPath);

            Path path = Paths.get(toPath);

            jsonObject.put("dest", PathUtils.toLinuxPath(path.getParent()));
            jsonObject.put("newname", path.getFileName());

            JSONArray jsonArray = new JSONArray();
            jsonArray.add(jsonObject);

            jsonObject = filemanager(jsonArray.toJSONString(), fromFileBucket.getFieldJson().getString("accessToken"), "copy");

            if (jsonObject.getInteger("errno") != 0) {
                throw new RuntimeException("文件复制失败！");
            }
        } else {

        }
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        String name = Paths.get(path).getFileName().toString();

        List<FileResource> list = propFind(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()), false);

        List<String> fileIds = new ArrayList<>();

        for (FileResource resource : list) {
            if (name.equals(resource.getName())) {
                fileIds.add(resource.getId());
            }
        }

        if (fileIds.isEmpty()) {
            throw new RuntimeException("获取下载地址失败！");
        }

        JSONArray jsonArray = getFileData(fileIds, fileBucket.getFieldJson().getString("accessToken"));

        JSONObject jsonObject = jsonArray.getJSONObject(0);

        String url = jsonObject.getString("dlink") + "&access_token=" + fileBucket.getFieldJson().getString("accessToken");

        header.put("User-Agent", "pan.baidu.com\t");

        return url;
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        JSONObject jsonObject = getUserData(fileBucket.getFieldJson().getString("accessToken"));
        if (jsonObject.getInteger("errno") != 0) {
            return "working";
        } else {
            return "error";
        }
    }

    private JSONObject getUserData(String accessToken) {
        String url = BASE_URL + "/rest/2.0/xpan/nas?method=uinfo&access_token=" + accessToken;
        HttpResponse response = HttpRequest.get(url)
                .execute();

        return JSONObject.parseObject(response.body());
    }


    private JSONArray getFileData(List<String> fileIds, String accessToken) {
        List<Long> list = new ArrayList<>();
        fileIds.forEach(fileId -> {
            list.add(Long.parseLong(fileId));
        });


        String url = BASE_URL + "/rest/2.0/xpan/multimedia?method=filemetas&dlink=1&access_token=" + accessToken + "&fsids=" + URLEncoder.createQuery().encode(JSONObject.toJSONString(list), StandardCharsets.UTF_8);

        HttpResponse response = HttpRequest.get(url)
                .execute();

        JSONObject jsonObject = JSONObject.parseObject(response.body());
        if (jsonObject.getInteger("errno") == 0) {
            return jsonObject.getJSONArray("list");
        }
        throw new RuntimeException("文件获取失败");
    }

    private JSONObject filemanager(String fileList, String accessToken, String opera) throws IOException {
        String url = BASE_URL + "/rest/2.0/xpan/file?method=filemanager&access_token=" + accessToken + "&opera=" + opera;

        OkHttpClient client = new OkHttpClient();

        RequestBody formBody = new FormBody.Builder()
                .add("async", "1")
                .add("filelist", fileList)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(formBody)
                .build();

        Response response = client.newCall(request).execute();

        return JSONObject.parseObject(response.body().string());
    }
}
