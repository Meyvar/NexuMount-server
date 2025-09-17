package cn.joker.webdav.webdav.adapter;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.fileUpload.ProgressRequestBody;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

@AdapterComponent(title = "123云盘")
public class Pan123Adapter implements IFileAdapter {

    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    @ParamAnnotation(label = "refreshToken")
    private String refreshToken;

    private final static String BASIC_URL = "https://open-api.123pan.com";

    @Autowired
    private FilePathCacheService filePathCacheService;

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        if ("/".equals(uri)) {
            FileResource fileResource = new FileResource();
            fileResource.setId("0");
            return fileResource;
        }
        return IFileAdapter.super.getFolderItself(fileBucket, uri);
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        List<FileResource> list = filePathCacheService.get(fileBucket.getUuid() + fileBucket.getPath() + uri);

        if (list != null && !list.isEmpty()) {
            return list;
        }

        list = new ArrayList<>();

        JSONObject param = new JSONObject();

        FileResource thisFile = getFolderItself(fileBucket, uri);


        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        int lastFileId = 0;
        while (true) {
            param.put("parentFileId", thisFile.getId());
            param.put("limit", 100);
            if (lastFileId != 0) {
                param.put("lastFileId", lastFileId);
            }

            String urlParam = "";

            for (String key : param.keySet()) {
                if (StringUtils.hasText(urlParam)) {
                    urlParam += "&";
                } else {
                    urlParam = "?";
                }
                urlParam += key + "=" + param.get(key);
            }

            JSONObject resp = request(fileBucket, "GET", "/api/v2/file/list" + urlParam, null);

            JSONArray fileList = resp.getJSONArray("fileList");

            for (int i = 0; i < fileList.size(); i++) {
                JSONObject file = fileList.getJSONObject(i);

                if (file.getInteger("trashed") == 1) {
                    continue;
                }

                FileResource fileResource = new FileResource();

                fileResource.setId(file.getString("fileId"));
                fileResource.setName(file.getString("filename"));
                fileResource.setSize(file.getLong("size"));
                fileResource.setType(file.getInteger("type") == 1 ? "folder" : "file");
                try {
                    fileResource.setDate(format.parse(file.getString("updateAt")));
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
                fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));

                list.add(fileResource);
            }

            if (resp.getInteger("lastFileId") == -1) {
                break;
            } else {
                lastFileId = resp.getInteger("lastFileId");
            }
        }

        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);

        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        String downloadUrl = getDownloadUrl(fileBucket, path, null);
        URI uri = new URI(downloadUrl);
        String encodedUrl = uri.toASCIIString();
        RequestHolder.getResponse().sendRedirect(encodedUrl);
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {

        FileResource fileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

        String fileNAme = Paths.get(path).getFileName().toString();


        if (fileResource == null) {
            String[] paths = path.split("/");
            List<String> pathArr = new ArrayList<>();
            for (String s : paths) {
                if (!StringUtils.hasText(s) || s.equals(fileNAme)) {
                    continue;
                }
                pathArr.add(s);
                String tempPath = "/" + String.join("/", pathArr);
                if (!hasPath(fileBucket, tempPath)) {
                    synchronized (this) {
                        if (!hasPath(fileBucket, tempPath)) {
                            mkcol(fileBucket, tempPath);
                            String tempParent = PathUtils.toLinuxPath(Paths.get(tempPath).getParent());
                            filePathCacheService.remove(fileBucket.getUuid() + fileBucket.getPath() + tempParent);
                        }
                    }
                }
            }
            fileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));
        }

        String md5 = DigestUtil.md5Hex(tempFilePath.toFile());


        JSONObject param = new JSONObject();
        param.put("parentFileID", fileResource.getId());
        param.put("filename", Paths.get(path).getFileName().toString());
        param.put("etag", md5);
        param.put("size", tempFilePath.toFile().length());

        JSONObject createResp = request(fileBucket, "POST", "/upload/v2/file/create", param);

        if (createResp.getBoolean("reuse")) {
            return;
        }

        long sliceSize = createResp.getLong("sliceSize");
        String uploadUrl = createResp.getJSONArray("servers").getString(0) + "/upload/v2/file/slice";
        String preuploadID = createResp.getString("preuploadID");

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

                if (currentSize + bytesRead > sliceSize) {
                    // 当前分片已满，写入剩余部分到新文件
                    int remain = (int) (sliceSize - currentSize);
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

        OkHttpClient client = new OkHttpClient();


        for (int i = 0; i < fragments.size(); i++) {
            File file = fragments.get(i);
            RequestBody uploadBody = new ProgressRequestBody(file, tempFilePath.toFile().length(), i * sliceSize, hook, MediaType.parse("application/octet-stream"));

            MultipartBody multipartBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("sliceNo", String.valueOf(i + 1))
                    .addFormDataPart("sliceMD5", md5)
                    .addFormDataPart("preuploadID", preuploadID)
                    .addFormDataPart("slice", path, uploadBody)
                    .build();

            Request uploadRequest = new Request.Builder()
                    .url(uploadUrl)
                    .header("Authorization", "Bearer " + fileBucket.getFieldJson().getString("accessToken"))
                    .header("Platform", "open_platform")
                    .post(multipartBody)
                    .build();

            Response uploadResponse = client.newCall(uploadRequest).execute();

            JSONObject uploadResp = JSONObject.parseObject(uploadResponse.body().string());

            if (uploadResp.getInteger("code") != 0) {
                throw new RuntimeException(uploadResp.getString("message"));
            }
        }

        JSONObject completeParam = new  JSONObject();
        completeParam.put("preuploadID", preuploadID);
        request(fileBucket, "POST", "/upload/v2/file/upload_complete", completeParam);

    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        FileResource thisFile = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("fileIDs", Collections.singletonList(thisFile.getId()));
        request(fileBucket, "POST", "/api/v1/file/trash", param);
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        FileResource thisFile = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

        JSONObject param = new JSONObject();
        param.put("parentID", thisFile.getId());
        param.put("name", Paths.get(path).getFileName().toString());

        request(fileBucket, "POST", "/upload/v1/file/mkdir", param);
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        FileResource thisFile = getFolderItself(fromFileBucket, fromPath);
        if (!Paths.get(fromPath).getFileName().toString().equals(Paths.get(toPath).getFileName().toString())) {
            rename(fromFileBucket, thisFile.getId(), Paths.get(toPath).getFileName().toString());
            return;
        }

        FileResource toFile = getFolderItself(toFileBucket, PathUtils.toLinuxPath(Paths.get(toPath).getParent()));

        JSONObject param = new JSONObject();
        param.put("fileIDs", Collections.singletonList(thisFile.getId()));
        param.put("toParentFileID", toFile.getId());

        request(fromFileBucket, "POST", "/api/v1/file/move", param);

    }

    private void rename(FileBucket fileBucket, String fileId, String fileName) {
        JSONObject param = new JSONObject();
        param.put("fileId", fileId);
        param.put("fileName", fileName);

        request(fileBucket, "PUT", "/api/v1/file/name", param);
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        FileResource thisFile = getFolderItself(fromFileBucket, fromPath);
        FileResource toFile = getFolderItself(toFileBucket, PathUtils.toLinuxPath(Paths.get(toPath).getParent()));

        JSONObject fileInfo = request(fromFileBucket, "GET", "/api/v1/file/detail?fileID=" + thisFile.getId(), null);

        JSONObject param = new JSONObject();
        param.put("parentFileID", toFile.getId());
        param.put("filename", Paths.get(toPath).getFileName().toString());
        param.put("etag", fileInfo.getString("etag"));
        param.put("size", fileInfo.getLong("size"));

        request(fromFileBucket, "POST", "/upload/v2/file/create", param);
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        FileResource thisFile = getFolderItself(fileBucket, path);
        JSONObject body = request(fileBucket, "GET", "/api/v1/file/download_info?fileId=" + thisFile.getId(), null);
        return body.getString("downloadUrl");
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        try {
            request(fileBucket, "GET", "/api/v1/user/info", null);
            return "working";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        JSONObject param = new JSONObject();
        param.put("authType", 5);
        param.put("trimAppId", "com.trim.cloudstorage");
        param.put("refreshToken", fileBucket.getFieldJson().getString("refreshToken"));

        String body = HttpRequest.post("https://oauth.fnnas.com/api/v1/oauth/refreshToken")
                .header("Content-Type", "application/json")
                .body(param.toJSONString())
                .execute()
                .body();

        JSONObject responseJson = JSONObject.parseObject(body);
        if (responseJson.getInteger("code") != 0) {
            return null;
        }

        responseJson = responseJson.getJSONObject("data").getJSONObject("tokenInfo");

        fileBucket.getFieldJson().put("accessToken", responseJson.getString("accessToken"));
        fileBucket.getFieldJson().put("refreshToken", responseJson.getString("refreshToken"));

        return fileBucket;
    }


    private JSONObject request(FileBucket fileBucket, String method, String api, JSONObject requestBody) {
        String url = BASIC_URL + api;

        String bodyStr = "";

        if (requestBody != null) {
            bodyStr = JSON.toJSONString(requestBody);
        }

        String body = HttpUtil.createRequest(Method.valueOf(method), url)
                .header("Authorization", "Bearer " + fileBucket.getFieldJson().getString("accessToken"))
                .header("Platform", "open_platform")
                .contentType("application/json")
                .body(bodyStr)
                .execute()
                .body();
        JSONObject responseJson = JSONObject.parseObject(body);

        if (responseJson.getInteger("code") != 0) {
            throw new RuntimeException(responseJson.getString("message"));
        }
        return responseJson.getJSONObject("data");
    }
}
