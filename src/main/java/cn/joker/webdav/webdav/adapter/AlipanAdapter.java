package cn.joker.webdav.webdav.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.joker.webdav.business.entity.FileBucket;
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
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@AdapterComponent(title = "阿里网盘")
public class AlipanAdapter implements IFileAdapter {

    @Getter
    @Setter
    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    @Getter
    @Setter
    @ParamAnnotation(label = "refreshToken")
    private String refreshToken;

    @Autowired
    private TaskManager taskManager;

    @Autowired
    private FilePathCacheService filePathCacheService;

    @Autowired
    private ISysSettingService sysSettingService;

    private final String BASIC_API = "https://openapi.alipan.com";

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        if ("/".equals(path)) {
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
        if ("/".equals(uri)) {
            FileResource fileResource = new FileResource();
            fileResource.setId("root");
            fileResource.setDriveId(fileBucket.getFieldJson().getString("resource_drive_id"));
            return fileResource;
        }

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


        if (!StringUtils.hasText(fileBucket.getFieldJson().getString("default_drive_id"))) {
            getDrive(fileBucket);
        }

        if ("/".equals(uri)) {
            if (StringUtils.hasText(fileBucket.getFieldJson().getString("backup_drive_id"))) {
                FileResource fileResource = new FileResource();
                fileResource.setDriveId(fileBucket.getFieldJson().getString("backup_drive_id"));
                fileResource.setName("备份文件");
                fileResource.setType("folder");
                fileResource.setId("root");
                fileResource.setDate(new Date());
                list.add(fileResource);
            }
            list.addAll(list(fileBucket, "root", fileBucket.getFieldJson().getString("resource_drive_id")));
        } else {
            FileResource parentFileResource = getFolderItself(fileBucket, uri);

            list.addAll(list(fileBucket, parentFileResource.getId(), parentFileResource.getDriveId()));
        }


        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);

        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        FileResource fileResource = getFolderItself(fileBucket, path);
        String contentType = URLConnection.guessContentTypeFromName(fileResource.getName());

        String downloadUrl = getDownloadUrl(fileBucket, path, null);
//        if (contentType != null && contentType.contains("video")) {
//            JSONObject jsonObject = getVideoPreviewPlayInfo(fileBucket, fileResource);
//            downloadUrl = jsonObject.getString("url");
//            RequestHolder.getResponse().sendRedirect(downloadUrl);
//            return;
//        } else {
//            downloadUrl = ;
//        }

        HttpServletRequest request = RequestHolder.getRequest();
        Map<String, String> headerMap = new HashMap<>();

        if (request != null && StringUtils.hasText(request.getHeader("range"))) {
            headerMap.put("Range", request.getHeader("range"));
        }

        HttpServletResponse response = RequestHolder.getResponse();

        Headers headers = Headers.of(headerMap);

        Request httpRequest = new Request.Builder()
                .headers(headers)
                .url(downloadUrl)
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

    private JSONObject getVideoPreviewPlayInfo(FileBucket fileBucket, FileResource fileResource) throws IOException {
        String url = BASIC_API + "/adrive/v1.0/openFile/getVideoPreviewPlayInfo";
        JSONObject param = new JSONObject();
        param.put("drive_id", fileResource.getDriveId());
        param.put("file_id", fileResource.getId());
        param.put("category", "live_transcoding");

        String body = HttpRequest.post(url)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .body(param.toJSONString())
                .execute().body();

        JSONObject jsonObject = JSON.parseObject(body).getJSONObject("video_preview_play_info");

        JSONArray jsonArray = jsonObject.getJSONArray("live_transcoding_task_list");

        for (int i = 0; i < jsonArray.size(); i++) {
            if (StringUtils.hasText(jsonArray.getJSONObject(i).getString("url"))) {
                jsonObject = jsonArray.getJSONObject(i);
            }
        }

        return jsonObject;
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {
        String createUrl = BASIC_API + "/adrive/v1.0/openFile/create";
        FileResource parentFileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

        JSONObject param = new JSONObject();
        param.put("drive_id", parentFileResource.getDriveId());
        param.put("parent_file_id", parentFileResource.getId());
        param.put("name", Paths.get(path).getFileName().toString());
        param.put("type", "file");
        param.put("check_name_mode", "auto_rename");

        HttpRequest.post(createUrl)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .body(param.toJSONString())
                .execute();
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        String url = BASIC_API + "/adrive/v1.0/openFile/recyclebin/trash";

        FileResource fileResource = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("drive_id", fileResource.getDriveId());
        param.put("file_id", fileResource.getId());

        HttpRequest.post(url)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .body(param.toJSONString())
                .execute();
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        String url = BASIC_API + "/adrive/v1.0/openFile/create";
        FileResource parentFileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));


        JSONObject param = new JSONObject();
        param.put("drive_id", parentFileResource.getDriveId());
        param.put("parent_file_id", parentFileResource.getId());
        param.put("name", Paths.get(path).getFileName().toString());
        param.put("type", "folder");
        param.put("check_name_mode", "auto_rename");

        HttpRequest.post(url)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .body(param.toJSONString())
                .execute();
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {

            FileResource fromFileResource = getFolderItself(fromFileBucket, fromPath);
            FileResource toParentFileResource = getFolderItself(fromFileBucket, PathUtils.toLinuxPath(Paths.get(toPath).getParent()));

            if (fromFileResource.getDriveId().equals(toParentFileResource.getDriveId())) {
                FileResource fromParentFileResource = getFolderItself(fromFileBucket, PathUtils.toLinuxPath(Paths.get(fromPath).getParent()));

                if (fromParentFileResource.getId().equals(toParentFileResource.getId())) {
                    //重命名
                    String url = BASIC_API + "/adrive/v1.0/openFile/update";
                    JSONObject param = new JSONObject();
                    param.put("drive_id", fromFileResource.getDriveId());
                    param.put("file_id", fromFileResource.getId());
                    param.put("check_name_mode", "auto_rename");
                    param.put("name", Paths.get(toPath).getFileName().toString());

                    HttpRequest.post(url)
                            .contentType("application/json")
                            .header("authorization", fromFileBucket.getFieldJson().getString("accessToken"))
                            .body(param.toJSONString())
                            .execute();
                } else {
                    //移动
                    String url = BASIC_API + "/adrive/v1.0/openFile/move";
                    JSONObject param = new JSONObject();
                    param.put("drive_id", fromFileResource.getDriveId());
                    param.put("file_id", fromFileResource.getId());
                    param.put("to_drive_id", toParentFileResource.getDriveId());
                    param.put("to_parent_file_id", toParentFileResource.getId());
                    param.put("check_name_mode", "auto_rename");

                    HttpRequest.post(url)
                            .contentType("application/json")
                            .header("authorization", fromFileBucket.getFieldJson().getString("accessToken"))
                            .body(param.toJSONString())
                            .execute();
                }
            } else {
                String uuid = UUID.randomUUID().toString().replace("-", "");
                MoveTask moveTask = new MoveTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

                taskManager.startTask(uuid, moveTask, StpUtil.getTokenValue());
            }
        } else {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            MoveTask moveTask = new MoveTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

            taskManager.startTask(uuid, moveTask, StpUtil.getTokenValue());
        }

    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            String url = BASIC_API + "/adrive/v1.0/openFile/copy";

            FileResource fromFileResource = getFolderItself(fromFileBucket, fromPath);

            FileResource toParentFileResource = getFolderItself(fromFileBucket, PathUtils.toLinuxPath(Paths.get(toPath).getParent()));

            if (fromFileResource.getDriveId().equals(toParentFileResource.getDriveId())) {
                JSONObject param = new JSONObject();
                param.put("drive_id", fromFileResource.getDriveId());
                param.put("file_id", fromFileResource.getId());
                param.put("to_drive_id", toParentFileResource.getDriveId());
                param.put("to_parent_file_id", toParentFileResource.getId());
                param.put("auto_rename", true);

                HttpRequest.post(url)
                        .contentType("application/json")
                        .header("authorization", fromFileBucket.getFieldJson().getString("accessToken"))
                        .body(param.toJSONString())
                        .execute();
            } else {
                String uuid = UUID.randomUUID().toString().replace("-", "");
                CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

                taskManager.startTask(uuid, copyTask, StpUtil.getTokenValue());
            }
        } else {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

            taskManager.startTask(uuid, copyTask, StpUtil.getTokenValue());
        }
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        String url = BASIC_API + "/adrive/v1.0/openFile/getDownloadUrl";

        FileResource fileResource = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("drive_id", fileResource.getDriveId());
        param.put("file_id", fileResource.getId());
        param.put("expire_sec", 14400);

        String body = HttpRequest.post(url)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .body(param.toJSONString())
                .execute().body();

        JSONObject jsonObject = JSON.parseObject(body);

        return jsonObject.getString("url");
    }

    private List<FileResource> list(FileBucket fileBucket, String parentId, String driveId) {
        List<FileResource> list = new ArrayList<>();

        String url = BASIC_API + "/adrive/v1.0/openFile/list";
        String marker = "";

        JSONObject param = new JSONObject();
        param.put("drive_id", driveId);
        param.put("limit", 100);
        param.put("parent_file_id", parentId);
        param.put("fields", "*");
        do {
            param.put("marker", marker);

            String body = HttpRequest.post(url)
                    .contentType("application/json")
                    .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                    .body(param.toJSONString())
                    .execute().body();

            JSONObject jsonObject = JSON.parseObject(body);

            JSONArray jsonArray = jsonObject.getJSONArray("items");


            for (int i = 0; i < jsonArray.size(); i++) {
                jsonObject = jsonArray.getJSONObject(i);
                FileResource fileResource = new FileResource();

                fileResource.setDriveId(jsonObject.getString("drive_id"));
                fileResource.setId(jsonObject.getString("file_id"));
                fileResource.setName(jsonObject.getString("name"));
                fileResource.setSize(jsonObject.getLong("size"));
                fileResource.setType(jsonObject.getString("type"));
                fileResource.setContentType(jsonObject.getString("mime_type"));

                Instant instant = Instant.parse(jsonObject.getString("updated_at"));
                fileResource.setDate(Date.from(instant));


                list.add(fileResource);
            }

            if (!StringUtils.hasText(jsonObject.getString("next_marker"))) {
                break;
            } else {
                marker = jsonObject.getString("next_marker");
            }
        } while (true);


        return list;
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        String default_drive_id = getDrive(fileBucket);
        if (StringUtils.hasText(default_drive_id)) {
            return "working";
        } else {
            return "error";
        }
    }

    private String getDrive(FileBucket fileBucket) {
        String url = BASIC_API + "/adrive/v1.0/user/getDriveInfo";

        String body = HttpRequest.post(url)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .execute().body();

        JSONObject jsonObject = JSON.parseObject(body);

        if ("AccessTokenExpired".equals(jsonObject.getString("code"))) {
            refreshToken(fileBucket);
            getDrive(fileBucket);
        }

        if (StringUtils.hasText(jsonObject.getString("default_drive_id"))) {
            fileBucket.getFieldJson().put("resource_drive_id", jsonObject.getString("resource_drive_id"));
            fileBucket.getFieldJson().put("backup_drive_id", jsonObject.getString("backup_drive_id"));
            fileBucket.getFieldJson().put("default_drive_id", jsonObject.getString("default_drive_id"));
            return jsonObject.getString("default_drive_id");
        } else {
            return "";
        }
    }

    public static void main(String[] args) {
        AlipanAdapter adapter = new AlipanAdapter();
        FileBucket fileBucket = new FileBucket();
        fileBucket.setFieldJson(new JSONObject());
        fileBucket.getFieldJson().put("accessToken", "eyJraWQiOiJLcU8iLCJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmNjMwNGMwMWRhYTI0YTM4ODQ4MDNhMGI5YzdkZWQ3NSIsImF1ZCI6IjZiNWI1MmUxNDRmNzQ4Zjc4YjNmOTZhMjYyNmVkNWQ3IiwicyI6ImNkYSIsImQiOiIxMjc2OTg1LDkzNTU4NzE5MiIsImlzcyI6ImFsaXBhbiIsImV4cCI6MTc1NjUzMDk0NSwibCI6NCwiaWF0IjoxNzU2NTIzNzQyLCJqdGkiOiI5ODY0YTdhMGNiNGI0MWQzYWNlZTU4ZGUxZjM5ZTgwZiJ9.z25xhi4gfRMYTNtXfoF-g-hiY_6LnKcMBJblBDMTikc");
        fileBucket.getFieldJson().put("refreshToken", "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJmNjMwNGMwMWRhYTI0YTM4ODQ4MDNhMGI5YzdkZWQ3NSIsImF1ZCI6IjZiNWI1MmUxNDRmNzQ4Zjc4YjNmOTZhMjYyNmVkNWQ3IiwiZXhwIjoxNzY0Mjk5NzQyLCJpYXQiOjE3NTY1MjM3NDIsImp0aSI6IjI0NzA1MjZjZjk4MzQ0ZDY4ZGQ3MWJmOGM5ZDAzNmRlIn0.rFgOoAxpoJf2hV60rt70Kn9kZeex8_t60hqOWRd3-K0aEnW8JxlMo0kUC4KIu0j7JpcnOiOKtLQyQ2A74tgaTw");

        adapter.workStatus(fileBucket);
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        try {
            Map<String, Object> token = handleToken(Map.of("refresh_token", fileBucket.getFieldJson().getString("refreshToken")));

            if (token.get("access_token") != null && StringUtils.hasText(token.get("access_token").toString())) {
                fileBucket.getFieldJson().put("accessToken", token.get("access_token"));
                fileBucket.getFieldJson().put("refreshToken", token.get("refresh_token"));
            } else {
                throw new RuntimeException("refresh token error");
            }

            fileBucket.getFieldJson().remove("resource_drive_id");
            fileBucket.getFieldJson().remove("backup_drive_id");
            fileBucket.getFieldJson().remove("default_drive_id");

            return fileBucket;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Map<String, Object> handleToken(Map<String, Object> body) throws IOException {
        Map<String, Object> requestInfo = generateRequestInfo("/v4/token", body);
        Map<String, String> headers = (Map<String, String>) requestInfo.get("headers");
        Map<String, Object> requestBody = (Map<String, Object>) requestInfo.get("body");
        String key = (String) requestInfo.get("key");

        JSONObject jsonBody = new JSONObject(requestBody);
        Request.Builder builder = new Request.Builder()
                .url("https://api.extscreen.com/aliyundrive/v4/token");

        headers.forEach(builder::addHeader);

        Request request = builder.post(okhttp3.RequestBody.create(JSON.toJSONString(jsonBody), MediaType.parse("application/json"))).build();
        Response response = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build().newCall(request).execute();
        if (!response.isSuccessful()) {
            return Map.of("refresh_token", "", "access_token", "", "text", "Failed to refresh token");
        }
        JSONObject respJson = JSON.parseObject(response.body().string());
        JSONObject data = respJson.getJSONObject("data");

        String cipherText = data.getString("ciphertext");
        String iv = data.getString("iv");

        String plain = decrypt(cipherText, key, iv);
        JSONObject token = JSON.parseObject(plain);

        return Map.of(
                "refresh_token", token.getString("refresh_token"),
                "access_token", token.getString("access_token"),
                "text", ""
        );
    }

    private String decrypt(String ciphertextB64, String keyStr, String ivStr) {
        byte[] ivBytes = HexUtil.decodeHex(ivStr); // Hutool HexUtil
        AES aes = new AES(Mode.CBC, Padding.PKCS5Padding, keyStr.getBytes(), ivBytes);
        byte[] decrypted = aes.decrypt(Base64.decode(ciphertextB64));
        return new String(decrypted, StandardCharsets.UTF_8);
    }


    private String randomString(int length) {
        return RandomUtil.randomString(length);
    }

    private Map<String, Object> generateRequestInfo(String apiPath, Map<String, Object> body) {
        String t = getTimestamp();
        String keyStr = generateKey(t);
        Map<String, String> headers = getParams(t);

        String bodyJson = JSON.toJSONString(body);
        String iv = randomString(16);
        String encrypted = encrypt(bodyJson, keyStr, iv);

        Map<String, Object> encryptedBody = new HashMap<>();
        encryptedBody.put("ciphertext", encrypted);
        encryptedBody.put("iv", iv);

        headers.put("Content-Type", "application/json");
        headers.put("sign", getSign(apiPath, t, headers.get("d"), keyStr));

        Map<String, Object> result = new HashMap<>();
        result.put("headers", headers);
        result.put("body", encryptedBody);
        result.put("key", keyStr);
        return result;
    }

    private String getSign(String apiPath, String t, String d, String key) {
        String data = String.format("POST-/api%s-%s-%s-%s", apiPath, t, d, key);
        return DigestUtil.sha256Hex(data);
    }

    private String encrypt(String plaintext, String keyStr, String ivStr) {
        AES aes = new AES(Mode.CBC, Padding.PKCS5Padding, keyStr.getBytes(StandardCharsets.UTF_8), ivStr.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = aes.encrypt(plaintext);
        return Base64.encode(encrypted);
    }

    private String getTimestamp() {
        try {
            Request request = new Request.Builder().url("https://api.extscreen.com/timestamp").get().build();
            Response response = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build().newCall(request).execute();
            if (!response.isSuccessful()) return String.valueOf(System.currentTimeMillis() / 1000);
            JSONObject json = JSON.parseObject(response.body().string());
            if (!"200".equals(String.valueOf(json.getInteger("code"))))
                return String.valueOf(System.currentTimeMillis() / 1000);
            JSONObject data = json.getJSONObject("data");
            return String.valueOf(data.getLong("timestamp"));
        } catch (IOException e) {
            return String.valueOf(System.currentTimeMillis() / 1000);
        }
    }

    private String generateKey(String t) {
        Map<String, String> params = getParams(t);
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String k : keys) if (!"t".equals(k)) sb.append(params.get(k));
        String hashed = h(sb.toString().toCharArray(), t);
        return DigestUtil.md5Hex(hashed);
    }

    private String h(char[] chars, String modifier) {
        LinkedHashSet<Character> set = new LinkedHashSet<>();
        for (char c : chars) set.add(c);
        int numericModifier = Integer.parseInt(modifier.substring(7));
        StringBuilder sb = new StringBuilder();
        for (char c : set) {
            int code = Math.abs(c - (numericModifier % 127) - 1);
            if (code < 33) code += 33;
            sb.append((char) code);
        }
        return sb.toString();
    }

    private Map<String, String> getParams(String t) {
        Map<String, String> params = new HashMap<>();
        params.put("akv", "2.8.1496");
        params.put("apv", "1.4.1");
        params.put("b", "vivo");
        params.put("d", "2c7d30cd7ae5e8017384988393f397c6");
        params.put("m", "V2329A");
        params.put("n", "V2329A");
        params.put("mac", "");
        params.put("wifiMac", "00db00200063");
        params.put("nonce", "");
        params.put("t", t);
        return params;
    }
}
