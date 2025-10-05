package cn.joker.webdav.webdav.adapter;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.fileUpload.UploadInputStream;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProvider;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.internal.Mimetypes;
import com.aliyun.oss.model.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

@AdapterComponent(title = "115生活")
public class Life115Adapter implements IFileAdapter {

    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    @ParamAnnotation(label = "refreshToken")
    private String refreshToken;

    private final static String BASIC_URL = "https://proapi.115.com";

    @Autowired
    private FilePathCacheService filePathCacheService;

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        if (uri.endsWith("/")) {
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

        FileResource thisFile = getFolderItself(fileBucket, uri);

        JSONObject param = new JSONObject();
        param.put("cid", thisFile.getId());
        param.put("show_dir", "1");

        int offset = 0;

        while (true) {
            param.put("offset", offset);
            param.put("limit", 500);

            String urlParam = "";

            for (String key : param.keySet()) {
                if (StringUtils.hasText(urlParam)) {
                    urlParam += "&";
                } else {
                    urlParam = "?";
                }
                urlParam += key + "=" + param.get(key);
            }

            JSONObject resp = request(fileBucket, "GET", "/open/ufile/files" + urlParam, null);

            JSONArray jsonArray = resp.getJSONArray("data");

            if (jsonArray == null || jsonArray.size() == 0) {
                break;
            }

            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);

                if (!jsonObject.getString("aid").equals("1")) {
                    continue;
                }

                FileResource fileResource = new FileResource();
                fileResource.setId(jsonObject.getString("fid"));
                fileResource.setType(jsonObject.getString("fc").equals("1") ? "file" : "folder");
                fileResource.setName(jsonObject.getString("fn"));
                fileResource.setDate(new Date(jsonObject.getLong("upt")));
                fileResource.setSize(jsonObject.getLong("fs"));
                fileResource.setPickCode(jsonObject.getString("pc"));
                fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));


                list.add(fileResource);
            }

            offset += 500;

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

    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook, String signKey, String signVal) throws Exception {
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


        String sha1 = DigestUtil.sha1Hex(tempFilePath.toFile());
        JSONObject uploadParam = new JSONObject();
        uploadParam.put("fileid", sha1);
        uploadParam.put("file_name", fileNAme);
        uploadParam.put("file_size", tempFilePath.toFile().length());
        uploadParam.put("target", "U_1_" + fileResource.getId());
        uploadParam.put("sign_key", signKey);
        uploadParam.put("sign_val", signVal);

        JSONObject initResp = request(fileBucket, "POST", "/open/upload/init", uploadParam);

        if (initResp.getInteger("status") == 2) {
            return;
        }


        if (Arrays.asList(700, 701, 702).contains(initResp.getInteger("code"))) {
            String[] signCheck = initResp.getString("sign_check").split("-");

            long start = Long.parseLong(signCheck[0]);
            long end = Long.parseLong(signCheck[1]);

            long length = end - start + 1;

            try (RandomAccessFile raf = new RandomAccessFile(tempFilePath.toFile(), "r")) {
                raf.seek(start);

                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] buffer = new byte[8192];
                long remaining = length;
                int read;
                while (remaining > 0 && (read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    md.update(buffer, 0, read);
                    remaining -= read;
                }

                byte[] digest = md.digest();
                String sha1Sing = HexUtil.encodeHexStr(digest);

                put(fileBucket, path, tempFilePath, hook, initResp.getString("sign_key"), sha1Sing.toUpperCase());
                return;
            }
        }


        JSONObject uploadToken = request(fileBucket, "GET", "/open/upload/get_token", null);


        // 使用DefaultCredentialProvider方法直接设置AK和SK
        CredentialsProvider credentialsProvider = new DefaultCredentialProvider(uploadToken.getString("AccessKeyId"), uploadToken.getString("AccessKeySecret"), uploadToken.getString("SecurityToken"));

        // 使用credentialsProvider初始化客户端
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        // 显式声明使用 V4 签名算法
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);

        String endpoint = uploadToken.getString("endpoint");

        String region = endpoint.replace("https://", "");
        region = region.replace("http://", "");
        region = region.split("\\.")[0];
        region = region.replace("oss-", "");

        String bucket = initResp.getString("bucket");
        String object = initResp.getString("object");


        // 创建OSSClient实例。
        // 当OSSClient实例不再使用时，调用shutdown方法以释放资源。
        OSS ossClient = OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .build();


        try {
            UploadInputStream inputStream = new UploadInputStream(tempFilePath.toFile(), hook, tempFilePath.toFile().length(), 0);


            JSONObject callback = initResp.getJSONObject("callback");
            JSONObject callbackInfo = JSONObject.parseObject(callback.getString("callback"));
            JSONObject callbackVal = JSONObject.parseObject(callback.getString("callback_var"));

            Callback ossCallback = new Callback();
            ossCallback.setCallbackUrl(callbackInfo.getString("callbackUrl"));

            ossCallback.setCalbackBodyType(Callback.CalbackBodyType.URL);
            ossCallback.setCallbackBody(callbackInfo.getString("callbackBody"));


            for (String key : callbackVal.keySet()) {
                ossCallback.addCallbackVar(key, callbackVal.getString(key));
            }


            // 创建PutObjectRequest对象。
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucket, object, inputStream);
            putObjectRequest.setCallback(ossCallback);
            // 创建PutObject请求。
            PutObjectResult result = ossClient.putObject(putObjectRequest);
            InputStream in = result.getResponse().getContent();

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {   // 循环读，直到流结束
                out.write(buffer, 0, len);
            }
            in.close();

            String response = out.toString(StandardCharsets.UTF_8);  // 指定编码，避免乱码

            JSONObject uploadResp = JSONObject.parseObject(response);

            if (!uploadResp.getBoolean("state")) {
                throw new RuntimeException(uploadResp.getString("message"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {
        put(fileBucket, path, tempFilePath, hook, null, null);
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        FileResource thisFile = getFolderItself(fileBucket, path);
        JSONObject param = new JSONObject();
        param.put("file_ids", thisFile.getId());

        request(fileBucket, "POST", "/open/ufile/delete", param);
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        FileResource thisFile = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

        JSONObject param = new JSONObject();
        param.put("pid", thisFile.getId());
        param.put("file_name", Paths.get(path).getFileName().toString());

        request(fileBucket, "POST", "/open/folder/add", param);
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
        param.put("file_ids", Collections.singletonList(thisFile.getId()));
        param.put("to_cid", toFile.getId());

        request(fromFileBucket, "POST", "/open/ufile/move", param);
    }

    private void rename(FileBucket fileBucket, String fileId, String fileName) {
        JSONObject param = new JSONObject();
        param.put("file_id", fileId);
        param.put("file_name", fileName);

        request(fileBucket, "POST", "/open/ufile/update", param);
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        FileResource toFile = getFolderItself(fromFileBucket, PathUtils.toLinuxPath(Paths.get(toPath).getParent()));
        FileResource thisFile = getFolderItself(fromFileBucket, fromPath);


        JSONObject param = new JSONObject();
        param.put("pid", toFile.getId());
        param.put("file_id", thisFile.getId());

        request(fromFileBucket, "POST", "/open/ufile/copy", param);
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        FileResource thisFile = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("pick_code", thisFile.getPickCode());

        JSONObject resp = request(fileBucket, "POST", "/open/ufile/downurl", param);

        String url = "";

        for (String key : resp.keySet()) {
            JSONObject jsonObject = resp.getJSONObject(key);
            jsonObject = jsonObject.getJSONObject("url");
            url = jsonObject.getString("url");
        }

        return url;
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        try {
            request(fileBucket, "GET", "/open/user/info", null);
            return "working";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        JSONObject param = new JSONObject();
        param.put("refresh_token", fileBucket.getFieldJson().getString("refreshToken"));

        JSONObject jsonObject = null;

        try {
            jsonObject = request(fileBucket, "POST", "https://passportapi.115.com/open/refreshToken", param);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }


        fileBucket.getFieldJson().put("accessToken", jsonObject.getString("access_token"));
        fileBucket.getFieldJson().put("refreshToken", jsonObject.getString("refresh_token"));

        return fileBucket;
    }

    private JSONObject request(FileBucket fileBucket, String method, String api, JSONObject requestBody) {
        String url = api;
        if (!api.startsWith("http")) {
            url = BASIC_URL + api;
        }

        HttpServletRequest request = RequestHolder.getRequest();

        String ua = "";

        if (request != null) {
            ua = request.getHeader("User-Agent");
        }

        String body = HttpUtil.createRequest(Method.valueOf(method), url)
                .header("Authorization", "Bearer " + fileBucket.getFieldJson().getString("accessToken"))
                .header("User-Agent", ua)
                .form(requestBody)
                .execute()
                .body();
        JSONObject responseJson = JSONObject.parseObject(body);

        if (!responseJson.getBoolean("state")) {
            throw new RuntimeException(responseJson.getString("message"));
        }

        if (api.startsWith("/open/ufile/files")) {
            return responseJson;
        }

        return responseJson.getJSONObject("data");
    }
}
