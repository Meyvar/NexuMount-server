package cn.joker.webdav.webdav.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.fileTask.taskImpl.CopyTask;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.fileUpload.ProgressRequestBody;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jcraft.jsch.ChannelSftp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.*;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@AdapterComponent(title = "夸克网盘")
public class QuarkAdapter implements IFileAdapter {

    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    @ParamAnnotation(label = "refreshToken")
    private String refreshToken;

    @ParamAnnotation(label = "signKey")
    private String signKey;

    @ParamAnnotation(label = "appId")
    private String appId;

    @Autowired
    private FilePathCacheService filePathCacheService;

    @Autowired
    private ISysSettingService sysSettingService;

    @Autowired
    private TaskManager taskManager;

    private static final String BASIC_URL = "https://open-api-drive.quark.cn";

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        if (uri.equals("/")) {
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


        JSONObject queryCursor = null;
        while (true) {
            FileResource thisRes = getFolderItself(fileBucket, uri);

            JSONObject param = new JSONObject();
            param.put("parent_fid", thisRes.getId());
            param.put("size", 100);
            param.put("sort", "file_type:asc,updated_at:desc");

            if (queryCursor != null) {
                param.put("query_cursor", queryCursor);
            }

            JSONObject responseJson = request(fileBucket, "POST", "/open/v1/file/list", param, null);

            JSONArray files = responseJson.getJSONArray("file_list");

            if (files == null) {
                break;
            }

            for (int i = 0; i < files.size(); i++) {
                JSONObject file = files.getJSONObject(i);

                FileResource fileResource = new FileResource();

                fileResource.setName(file.getString("filename"));
                fileResource.setId(file.getString("fid"));
                fileResource.setSize(file.getLong("size"));
                fileResource.setDate(new Date(file.getLong("updated_at")));
                fileResource.setType(file.getString("file_type").equals("0") ? "folder" : "file");
                fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));

                list.add(fileResource);
            }


            if (responseJson.getBoolean("last_page")) {
                break;
            }
            queryCursor = responseJson.getJSONObject("next_query_cursor");
        }


        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);
        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        Map<String, String> header = new HashMap<>();
        String downloadUrl = getDownloadUrl(fileBucket, path, header);

        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        if (request != null && StringUtils.hasText(request.getHeader("range"))) {
            header.put("Range", request.getHeader("range"));
        }


        Headers headers = Headers.of(header);

        Request httpRequest = new Request.Builder()
                .headers(headers)
                .url(downloadUrl)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response httpResponse = client.newCall(httpRequest).execute();

        if (!httpResponse.isSuccessful()) {
            throw new IOException("Unexpected code " + response);
        }

        if (request != null && StringUtils.hasText(request.getHeader("range"))) {
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        } else {
            response.setStatus(httpResponse.code());
        }


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
        String md5 = DigestUtil.md5Hex(tempFilePath.toFile());
        String sha1 = DigestUtil.sha1Hex(tempFilePath.toFile());

        JSONObject userInfo = getUserInfo(fileBucket);

        long timestamp = System.currentTimeMillis();
        long size = tempFilePath.toFile().length();

        FileResource fileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

        if (fileResource == null) {
            String[] paths = path.split("/");
            List<String> pathArr = new ArrayList<>();

            for (String s : paths) {
                if (!StringUtils.hasText(s) || s.equals(Paths.get(path).getFileName().toString())) {
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

        JSONObject resp = uploadPre(fileBucket, timestamp, tempFilePath.toFile(), userInfo, Paths.get(path).getFileName().toString(), md5, sha1, fileResource.getId());

        if (resp.getBoolean("finish")) {
            return;
        }

        long fragmentsSize = 20 * 1024 * 1024;

        List<JSONObject> partInfoList = new LinkedList<>();
        long partInfoListNum = (tempFilePath.toFile().length() + fragmentsSize - 1) / fragmentsSize;
        for (int i = 0; i < partInfoListNum; i++) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("part_number", i + 1);
            long partSize = fragmentsSize;

            if ((i + 1) * partSize > size) {
                partSize = size - (i * fragmentsSize);
            }

            jsonObject.put("part_size", partSize);
            partInfoList.add(jsonObject);
        }

        JSONObject uploadUrlJson = getUploadUrl(fileBucket, partInfoList, resp.getString("task_id"));

        //文件切片

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

        List<String> eTagList = new LinkedList<>();

        String sha256 = uploadUrlJson.getJSONObject("common_headers").getString("X-Oss-Content-Sha256");
        String date = uploadUrlJson.getJSONObject("common_headers").getString("X-Oss-Date");

        OkHttpClient client = new OkHttpClient();
        for (int i = 0; i < uploadUrlJson.getJSONArray("upload_urls").size(); i++) {
            JSONObject jsonObject = uploadUrlJson.getJSONArray("upload_urls").getJSONObject(i);

            String uploadUrl = jsonObject.getString("upload_url");
            String signature = jsonObject.getJSONObject("signature_info").getString("signature");

            File fragmentFile = fragments.get(i);

            RequestBody requestBody = new ProgressRequestBody(fragmentFile, tempFilePath.toFile().length(), fragmentsSize * (i + 1), hook, null);


            Request uploadRequest = new Request.Builder()
                    .header("Authorization", signature)
                    .header("Accept-Encoding", "gzip")
                    .header("X-Oss-Content-Sha256", sha256)
                    .header("X-Oss-Date", date)
                    .url(uploadUrl)
                    .put(requestBody)
                    .build();

            Response uploadResp = client.newCall(uploadRequest).execute();

            if (!uploadResp.isSuccessful()) {
                throw new RuntimeException("upload failed status:" + uploadResp.code());
            }


            eTagList.add(uploadResp.header("ETag"));
        }


        if (eTagList.size() != partInfoList.size()) {
            throw new RuntimeException("upload part size error");
        }

        for (int i = 0; i < partInfoList.size(); i++) {
            partInfoList.get(i).put("etag", eTagList.get(i));
        }

        JSONObject finishParam = new JSONObject();
        finishParam.put("task_id", resp.getString("task_id"));
        finishParam.put("part_info_list", partInfoList);

        request(fileBucket, "POST", "/open/v1/file/upload_finish", finishParam, null);
    }

    private JSONObject getUploadUrl(FileBucket fileBucket, List<JSONObject> partInfoList, String taskId) {

        JSONObject param = new JSONObject();
        param.put("task_id", taskId);
        param.put("part_info_list", partInfoList);

        JSONObject responseJson = request(fileBucket, "POST", "/open/v1/file/get_upload_urls", param, null);

        return responseJson.getJSONObject("data");
    }

    private JSONObject uploadPre(FileBucket fileBucket, long timestamp, File file, JSONObject userInfo, String fileName, String md5, String sha1, String parentId) throws Exception {
        Map<String, String> signMap = generateReqSign("POST", "/open/v1/file/upload_pre", fileBucket.getFieldJson().getString("signKey"));
        Map<String, String> headerMap = getHeaders(signMap.get("timestamp"), signMap.get("tokenHash"), fileBucket.getFieldJson().getString("appId"));

        Map<String, String> proof = generateProof(file, signMap.get("tokenHash"), userInfo);

        JSONObject param = new JSONObject();
        param.put("file_name", fileName);
        param.put("size", file.length());
        Tika tika = new Tika();
        param.put("format_type", tika.detect(file));
        param.put("md5", md5);
        param.put("sha1", sha1);
        param.put("l_created_at", file.lastModified());
        param.put("l_updated_at", file.lastModified());
        param.put("pdir_fid", parentId);
        param.put("same_path_reuse", true);
        param.put("proof_version", proof.get("proofVersion"));
        param.put("proof_seed1", proof.get("proofSeed1"));
        param.put("proof_seed2", proof.get("proofSeed2"));
        param.put("proof_code1", proof.get("proofCode1"));
        param.put("proof_code2", proof.get("proofCode2"));

        String url = BASIC_URL + "/open/v1/file/upload_pre";
        url += "?req_id=" + signMap.get("uuid") + "&access_token=" + fileBucket.getFieldJson().getString("accessToken");


        String body = HttpUtil.createPost(url)
                .body(param.toJSONString())
                .headerMap(headerMap, false)
                .execute()
                .body();

        JSONObject responseJson = JSONObject.parseObject(body);
        if (responseJson.getInteger("status") != 0) {
            throw new RuntimeException(responseJson.getString("error_info"));
        }

        return responseJson.getJSONObject("data");

    }

    private Map<String, String> generateProof(File file, String token, JSONObject userInfo) {

        Map<String, String> map = new HashMap<>();

        String userId = userInfo.getString("user_id");

        map.put("proofVersion", "v1");
        map.put("proofSeed1", DigestUtil.md5Hex(userId + token));
        map.put("proofSeed2", DigestUtil.md5Hex(String.valueOf(file.length())));
        map.put("proofCode1", generateProofCode(file, map.get("proofSeed1")));
        map.put("proofCode2", generateProofCode(file, map.get("proofSeed2")));

        return map;
    }

    private String generateProofCode(File file, String proofSeed1) {
        Map<String, Integer> proofRange = getProofRange(proofSeed1, file.length());

        if (proofRange.get("Start") == null) {
            return "";
        }

        int start = proofRange.get("Start");
        int length = proofRange.get("End") - proofRange.get("Start");
        if (length == 0) {
            return "";
        }

        byte[] buffer = new byte[length];

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start); // 移动到指定位置
            raf.readFully(buffer); // 读取指定字节
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Base64.getEncoder().encodeToString(buffer);
    }

    private Map<String, Integer> getProofRange(String proofSeed, long size) {
        Map<String, Integer> map = new HashMap<>();
        if (size == 0) {
            map.put("Start", 0);
            map.put("End", 0);
            return map;
        }

        String md5Hash = DigestUtil.md5Hex(proofSeed);
        String subMd5 = md5Hash.substring(0, 16);
        long md5Long = Long.parseUnsignedLong(subMd5, 16);
        long index = Long.remainderUnsigned(md5Long, size);

        map.put("Start", (int) index);
        map.put("End", (int) index + 8);

        if (map.get("End") > size) {
            map.put("End", (int) size);
        }

        return map;
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        FileResource fileResource = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("fid_list", Collections.singletonList(fileResource.getId()));
        param.put("action_type", 1);

        request(fileBucket, "POST", "/open/v1/file/delete", param, null);
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        FileResource fileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

        JSONObject param = new JSONObject();
        param.put("pdir_fid", fileResource.getId());
        param.put("dir_path", Paths.get(path).getFileName());

        request(fileBucket, "POST", "/open/v1/dir", param, null);

        do {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            filePathCacheService.remove(fileBucket.getUuid() + fileBucket.getPath() + PathUtils.toLinuxPath(Paths.get(path).getParent()));
            fileResource = getFolderItself(fileBucket, path);
        } while (fileResource == null);
    }

    private void rename(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        FileResource fileResource = getFolderItself(fromFileBucket, fromPath);

        JSONObject param = new JSONObject();
        param.put("conflict_mode", "REUSE");
        param.put("fid", fileResource.getId());
        param.put("file_name", Paths.get(toPath).getFileName());

        request(fromFileBucket, "POST", "/open/v1/file/rename", param, null);

        do {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String temp = PathUtils.toLinuxPath(Paths.get(toPath).getParent());
            filePathCacheService.remove(toFileBucket.getUuid() + toFileBucket.getPath() + temp);
            fileResource = getFolderItself(toFileBucket, toPath);
        } while (fileResource == null);
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (!Paths.get(fromPath).getFileName().equals(Paths.get(toPath).getFileName())) {
            rename(fromFileBucket, fromPath, toFileBucket, toPath);
            return;
        }

        FileResource fileResource = getFolderItself(fromFileBucket, fromPath);
        FileResource toFileResource = getFolderItself(fromFileBucket, PathUtils.toLinuxPath(Paths.get(toPath).getParent()));

        JSONObject param = new JSONObject();
        param.put("action_type", 1);
        param.put("fid_list", Collections.singletonList(fileResource.getId()));
        param.put("to_pdir_fid", toFileResource.getId());


        request(fromFileBucket, "POST", "/open/v1/file/move", param, null);

        do {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            String temp = PathUtils.toLinuxPath(Paths.get(toPath).getParent());
            filePathCacheService.remove(toFileBucket.getUuid() + toFileBucket.getPath() + temp);
            fileResource = getFolderItself(toFileBucket, toPath);
        } while (fileResource == null);
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

        taskManager.startTask(uuid, copyTask, StpUtil.getTokenValue());
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        String cookie = "x_pan_client_id=" + fileBucket.getFieldJson().getString("appId")
                + "; x_pan_access_token=" + fileBucket.getFieldJson().getString("accessToken");
        header.put("Cookie", cookie);

        FileResource fileResource = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("fid", fileResource.getId());

        JSONObject responseJson = request(fileBucket, "POST", "/open/v1/file/get_download_url", param, header);
        return responseJson.getString("download_url");
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        getUserInfo(fileBucket);
        return "working";
    }

    private JSONObject getUserInfo(FileBucket fileBucket) {
        return request(fileBucket, "GET", "/open/v1/user/info", null, null);
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        JSONObject param = new JSONObject();
        param.put("authType", 4);
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
        fileBucket.getFieldJson().put("appId", responseJson.getString("appId"));
        fileBucket.getFieldJson().put("signKey", responseJson.getString("signKey"));

        return fileBucket;
    }

    private Map<String, String> generateReqSign(String method, String api, String signKey) {
        long timestamp = System.currentTimeMillis();

        String tokenData = method + "&" + api + "&" + timestamp + "&" + signKey;
        String tokenHash = DigestUtil.sha256Hex(tokenData);
        String uuid = UUID.randomUUID().toString();

        Map<String, String> map = new HashMap<>();
        map.put("timestamp", String.valueOf(timestamp));
        map.put("tokenHash", tokenHash);
        map.put("uuid", uuid);
        return map;
    }

    private Map<String, String> getHeaders(String timestamp, String token, String appId) {
        Map<String, String> map = new HashMap<>();
        map.put("Accept", "application/json, text/plain, */*");
        map.put("x-pan-tm", timestamp);
        map.put("x-pan-token", token);
        map.put("x-pan-client-id", appId);
        return map;
    }


    private JSONObject request(FileBucket fileBucket, String method, String api, JSONObject requestBody, Map<String, String> headers) {
        Map<String, String> signMap = generateReqSign(method, api, fileBucket.getFieldJson().getString("signKey"));
        Map<String, String> headerMap = getHeaders(signMap.get("timestamp"), signMap.get("tokenHash"), fileBucket.getFieldJson().getString("appId"));

        if (headers != null) {
            headerMap.putAll(headers);
        }

        String url = BASIC_URL + api;
        url += "?req_id=" + signMap.get("uuid") + "&access_token=" + fileBucket.getFieldJson().getString("accessToken");


        String bodyStr = "";
        if (requestBody != null) {
            bodyStr = requestBody.toJSONString();
        }


        String body = HttpUtil.createRequest(Method.valueOf(method), url)
                .headerMap(headerMap, false)
                .body(bodyStr)
                .execute()
                .body();
        JSONObject responseJson = JSONObject.parseObject(body);
        if (responseJson.getInteger("status") != 0) {
            throw new RuntimeException(responseJson.getString("error_info"));
        }

        return responseJson.getJSONObject("data");
    }
}
