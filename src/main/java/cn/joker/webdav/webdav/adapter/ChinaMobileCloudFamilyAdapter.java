package cn.joker.webdav.webdav.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.codec.Base64;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.XmlUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.TaskMeta;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.fileTask.taskImpl.CopyTask;
import cn.joker.webdav.fileTask.taskImpl.MoveTask;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@AdapterComponent(title = "中国移动云盘（家庭）")
public class ChinaMobileCloudFamilyAdapter implements IFileAdapter {

    @ParamAnnotation(label = "家庭ID")
    private String familyId;

    @ParamAnnotation(label = "authorization")
    private String authorization;

    @Autowired
    private FilePathCacheService filePathCacheService;

    @Autowired
    private TaskManager taskManager;

    @Autowired
    private ISysSettingService sysSettingService;

    private static final String BASIC_URL = "https://yun.139.com/orchestration/familyCloud-rebuild";

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        if (uri.equals("/")) {

            List<FileResource> list = filePathCacheService.get(fileBucket.getUuid() + fileBucket.getPath() + "/" + fileBucket.getUuid());

            if (list != null && !list.isEmpty()) {
                return list.getFirst();
            }


            JSONObject param = new JSONObject();
            param.put("catalogType", 3);
            param.put("sortDirection", 1);
            param.put("contentSortType", 0);
            JSONObject page = new JSONObject();
            page.put("pageNum", 1);
            page.put("pageSize", 2);
            param.put("page", page);

            JSONObject resp = request(fileBucket, "POST", "/content/v1.2/queryContentList", param);

            FileResource resource = new FileResource();
            resource.setId(resp.getString("path"));

            list = new ArrayList<>();
            list.add(resource);

            filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + "/" + fileBucket.getUuid(), list);

            return list.getFirst();
        } else if (uri.equals("/家庭音乐（移动网盘系统自建）")) {
            List<FileResource> list = filePathCacheService.get(fileBucket.getUuid() + fileBucket.getPath() + "/" + fileBucket.getUuid() + "/" + fileBucket.getUuid());

            if (list != null && !list.isEmpty()) {
                return list.getFirst();
            }


            JSONObject param = new JSONObject();
            param.put("catalogType", 2);
            param.put("sortDirection", 1);
            param.put("contentSortType", 0);
            JSONObject page = new JSONObject();
            page.put("pageNum", 1);
            page.put("pageSize", 2);
            param.put("page", page);

            JSONObject resp = request(fileBucket, "POST", "/content/v1.2/queryContentList", param);

            FileResource resource = new FileResource();
            resource.setId(resp.getString("path"));

            list = new ArrayList<>();
            list.add(resource);

            filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + "/" + fileBucket.getUuid() + "/" + fileBucket.getUuid(), list);

            return list.getFirst();
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


        if (uri.equals("/")) {
            FileResource music = new FileResource();
            music.setName("家庭音乐（移动网盘系统自建）");
            music.setType("folder");
            music.setId("");
            music.setDate(new Date());

            list.add(music);

            param.put("catalogType", 3);
        } else {
            if (uri.startsWith("/家庭音乐（移动网盘系统自建）")) {
                param.put("catalogType", 2);
            } else {
                param.put("catalogType", 3);
                param.put("catalogID", thisFile.getId());
            }

        }


        param.put("sortDirection", 1);
        param.put("contentSortType", 0);


        int pageNum = 1;
        while (true) {
            JSONObject page = new JSONObject();
            page.put("pageNum", pageNum++);
            page.put("pageSize", 100);
            param.put("page", page);

            JSONObject resp = request(fileBucket, "POST", "/content/v1.2/queryContentList", param);

            JSONArray cloudCatalogList = resp.getJSONArray("cloudCatalogList");

            for (int i = 0; i < cloudCatalogList.size(); i++) {
                JSONObject item = cloudCatalogList.getJSONObject(i);

                FileResource fileResource = new FileResource();
                fileResource.setId(item.getString("catalogID"));
                fileResource.setName(item.getString("catalogName"));
                fileResource.setType("folder");
                fileResource.setDate(DateUtil.parse(item.getString("lastUpdateTime"), "yyyyMMddHHmmss"));


                list.add(fileResource);
            }

            JSONArray cloudContentList = resp.getJSONArray("cloudContentList");

            for (int i = 0; i < cloudContentList.size(); i++) {
                JSONObject item = cloudContentList.getJSONObject(i);

                FileResource fileResource = new FileResource();
                fileResource.setId(item.getString("contentID"));
                fileResource.setName(item.getString("contentName"));
                fileResource.setType("file");
                fileResource.setSize(item.getLong("contentSize"));
                fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));
                fileResource.setDate(DateUtil.parse(item.getString("lastUpdateTime"), "yyyyMMddHHmmss"));


                list.add(fileResource);
            }


            if (resp.getInteger("totalCount") < 100) {
                break;
            }
        }


        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);
        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        String downloadUrl = getDownloadUrl(fileBucket, path, null);
        RequestHolder.getResponse().sendRedirect(downloadUrl);
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {
        String url = "https://group.yun.139.com/hcy/group/dynamic/file/create";
        int catalogType = 3;
        if (path.startsWith("/家庭音乐（移动网盘系统自建）")) {
            catalogType = 2;
        }

        String fileNAme = Paths.get(path).getFileName().toString();

        FileResource fileResource = getFolderItself(fileBucket, PathUtils.toLinuxPath(Paths.get(path).getParent()));

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

        String contentHash = DigestUtil.sha256Hex(tempFilePath.toFile());

        JSONObject createParam = new JSONObject();
        createParam.put("catalogType", catalogType);
        createParam.put("contentHashAlgorithm", "SHA256");
        createParam.put("contentHash", contentHash);
        createParam.put("contentType", "application/octet-stream");
        createParam.put("fileRenameMode", "auto_rename");
        createParam.put("name", fileNAme);
        createParam.put("groupId", fileBucket.getFieldJson().getString("familyId"));
        createParam.put("groupType", 1);
        createParam.put("parentFileId", fileResource.getId().replace("root:/", ""));
        createParam.put("seqNo", UUID.randomUUID().toString().replace("-", ""));
        createParam.put("size", tempFilePath.toFile().length());

        //分片信息

        long size = tempFilePath.toFile().length();
        double fragmentation = size / 20971520.0;

        int uploadSize = (int) Math.ceil(fragmentation);

        List<Map<String, Object>> jsonArray = new LinkedList<>();

        long parallelHashCtx = 0;
        for (int i = 0; i < uploadSize; i++) {
            Map<String, Object> partInfo = new JSONObject();

            partInfo.put("partNumber", i + 1);

            long partSize = 20971520;

            if ((parallelHashCtx + partSize) > size) {
                partSize = size - parallelHashCtx;
            }

            JSONObject parallelHashCtxJson = new JSONObject();
            parallelHashCtxJson.put("partOffset", parallelHashCtx);

            partInfo.put("parallelHashCtx", parallelHashCtxJson);
            partInfo.put("partSize", partSize);

            jsonArray.add(partInfo);

            parallelHashCtx += partSize;
        }

        List<List<Map<String, Object>>> result = new LinkedList<>();
        int listSize = jsonArray.size();
        for (int i = 0; i < listSize; i += 100) {
            int end = Math.min(listSize, i + 100);
            result.add(new LinkedList<>(jsonArray.subList(i, end)));
        }

        if (result.size() > 1) {
            createParam.put("partInfos", result.getFirst());
        } else {
            createParam.put("partInfos", jsonArray);
        }


        String body = HttpUtil.createPost(url)
                .addHeaders(getHeader("Basic " + fileBucket.getFieldJson().getString("authorization")))
                .body(createParam.toJSONString())
                .execute()
                .body();


        JSONObject createResp = JSONObject.parseObject(body);
        if (!createResp.getBoolean("success")) {
            throw new RuntimeException(createResp.getString("message"));
        }

        createResp = createResp.getJSONObject("data");

        if ((createResp.getBoolean("exist") != null && createResp.getBoolean("exist")) || createResp.getBoolean("rapidUpload")) {
            return;
        }

        JSONArray partInfos = JSONArray.parseArray(createResp.getJSONArray("partInfos").toJSONString());

        String uploadId = createResp.getString("uploadId");
        String fileId = createResp.getString("fileId");

        if (result.size() > 1) {
            url = "https://group.yun.139.com/hcy/group/dynamic/file/getUploadUrl";


            for (int i = 0; i < result.size(); i++) {
                if (i == 0) {
                    continue;
                }


                JSONObject jsonObject = new JSONObject();
                jsonObject.put("uploadId", uploadId);
                jsonObject.put("fileId", fileId);
                JSONObject commonAccountInfo = new JSONObject();

                String authorization = fileBucket.getFieldJson().getString("authorization");
                byte[] decodedBytes = java.util.Base64.getDecoder().decode(authorization);
                authorization = new String(decodedBytes);
                String[] authorizations = authorization.split("\\|");
                authorizations = authorizations[0].split(":");
                authorization = authorizations[1];

                commonAccountInfo.put("accountType", 1);
                commonAccountInfo.put("account", authorization);

                jsonObject.put("commonAccountInfo", commonAccountInfo);
                jsonObject.put("partInfos", result.get(i));
                jsonObject.put("groupId", fileBucket.getFieldJson().getString("familyId"));


                HttpResponse response = HttpRequest.post(url)
                        .addHeaders(getHeader("Basic " + fileBucket.getFieldJson().getString("authorization")))
                        .body(jsonObject.toJSONString())
                        .execute();

                if (response.isOk()) {
                    jsonObject = JSONObject.parseObject(response.body());
                    partInfos.addAll(JSONArray.parseArray(jsonObject.getJSONObject("data").getJSONArray("partInfos").toJSONString()));
                } else {
                    throw new RuntimeException("status is " + response.getStatus());
                }

            }
        }

        InputStream fis = new FileInputStream(tempFilePath.toFile());
        byte[] buffer = new byte[20971520];
        DecimalFormat df = new DecimalFormat("#.##");
        long startTime = System.currentTimeMillis();

        TaskMeta meta;
        if (hook != null) {
            meta = hook.getTaskMeta();
        } else {
            meta = null;
        }


        final long[] uploaded = {0};

        for (int i = 0; i < partInfos.size(); i++) {

            if (hook != null) {
                hook.pause();
                if (hook.cancel()) {
                    return;
                }
            }

            JSONObject jsonObject = partInfos.getJSONObject(i);

            url = jsonObject.getString("uploadUrl");


            long totalBytes = tempFilePath.toFile().length();

            int bytesRead = fis.read(buffer);

            byte[] partData = new byte[bytesRead];
            System.arraycopy(buffer, 0, partData, 0, bytesRead);


            RequestBody requestBody = new ChinaMobileCloudAdapter.ProgressRequestBody(partData, new ChinaMobileCloudAdapter.ProgressListener() {
                long lastUpdate = System.currentTimeMillis();

                @Override
                public void onProgress(long bytesWritten, long contentLength) {
                    uploaded[0] += bytesWritten;
                    if (meta != null) {
                        long now = System.currentTimeMillis();
                        if (now - lastUpdate >= 1000) { // 每秒输出一次
                            double progress = uploaded[0] * 100.0 / totalBytes;
                            double speedKB = uploaded[0] / 1024.0 / ((now - startTime) / 1000.0);
                            lastUpdate = now;

                            meta.setProgress(df.format(progress));
                            meta.setElapsed(df.format(speedKB));

                        }
                    }
                }
            });

            Request request = new Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/octet-stream")
                    .put(requestBody)
                    .build();

            OkHttpClient client = new OkHttpClient();
            Response okResponse = client.newCall(request).execute();

            if (!okResponse.isSuccessful()) {
                throw new RuntimeException("status is " + okResponse.code());
            }

        }

        fis.close();

        url = "https://group.yun.139.com/hcy/group/dynamic/file/complete";

        JSONObject completeParam = new JSONObject();
        completeParam.put("contentHash", contentHash);
        completeParam.put("contentHashAlgorithm", "SHA256");
        completeParam.put("fileId", fileId);
        completeParam.put("uploadId", uploadId);
        completeParam.put("groupId", fileBucket.getFieldJson().getString("familyId"));

        HttpResponse response = HttpRequest.post(url)
                .addHeaders(getHeader("Basic " + fileBucket.getFieldJson().getString("authorization")))
                .body(completeParam.toJSONString())
                .execute();

        if (response.isOk()) {
            JSONObject jsonObject = JSONObject.parseObject(response.body());
            if (!jsonObject.getBoolean("success")) {
                throw new RuntimeException(jsonObject.getString("message"));
            }
        } else {
            throw new RuntimeException("status is " + response.getStatus());
        }
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        if (path.equals("/家庭音乐（移动网盘系统自建）")) {
            throw new RuntimeException("无法创建");
        }

        FileResource thiaFile = getFolderItself(fileBucket, path);

        JSONObject param = new JSONObject();
        param.put("taskType", 2);
        param.put("sourceCatalogType", 1002);
        param.put("path", getFilePath(fileBucket, path));
        param.put("sourceCloudID", fileBucket.getFieldJson().getString("familyId"));

        JSONArray contentList = new JSONArray();
        JSONArray catalogList = new JSONArray();

        if (thiaFile.getType().equals("folder")) {
            catalogList.add(thiaFile.getId());
        } else {
            contentList.add(thiaFile.getId());
        }
        param.put("contentList", contentList);
        param.put("catalogList", catalogList);

        JSONObject resp = request(fileBucket, "POST", "/batchOprTask/v1.0/createBatchOprTask", param);

        String taskId = resp.getString("taskID");
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        if (path.startsWith("/家庭音乐（移动网盘系统自建）")) {
            throw new RuntimeException("无法创建");
        }

        JSONObject param = new JSONObject();
        param.put("docLibName", Paths.get(path).getFileName());
        param.put("path", getFilePath(fileBucket, path));

        request(fileBucket, "POST", "/cloudCatalog/v1.0/createCloudDoc", param);
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (Paths.get(fromPath).getParent().toString().equals(Paths.get(toPath).getParent().toString())) {

            FileResource thisFile = getFolderItself(fromFileBucket, fromPath);

            if (!thisFile.getType().equals("folder")) {
                throw new RuntimeException("无法修改文件夹名称");
            }

            JSONObject param = new JSONObject();
            param.put("contentName", Paths.get(toPath).getFileName());
            param.put("contentID", thisFile.getId());
            param.put("path", getFilePath(fromFileBucket, fromPath) + "/" + thisFile.getId());

            request(fromFileBucket, "POST", "/photoContent/v1.0/modifyContentInfo", param);
        } else {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            MoveTask moveTask = new MoveTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

            taskManager.startTask(uuid, moveTask, StpUtil.getTokenValue());
        }
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

        taskManager.startTask(uuid, copyTask, StpUtil.getTokenValue());
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        JSONObject param = new JSONObject();

        if (path.equals("/")) {
            param.put("catalogType", 3);
        } else {
            if (path.startsWith("/家庭音乐（移动网盘系统自建）")) {
                param.put("catalogType", 2);
            } else {
                param.put("catalogType", 3);
            }
        }

        FileResource thisFile = getFolderItself(fileBucket, path);

        param.put("contentID", thisFile.getId());

        JSONObject extInfo = new JSONObject();
        extInfo.put("isReturnCdnDownloadUrl", 1);

        param.put("extInfo", extInfo);
        param.put("path", getFilePath(fileBucket, path));


        JSONObject resp = request(fileBucket, "POST", "/content/v1.0/getFileDownLoadURL", param);

        return resp.getString("downloadURL");
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        JSONObject param = new JSONObject();
        param.put("catalogType", 3);
        param.put("sortDirection", 1);
        param.put("contentSortType", 0);
        JSONObject page = new JSONObject();
        page.put("pageNum", 1);
        page.put("pageSize", 2);
        param.put("page", page);

        try {
            request(fileBucket, "POST", "/content/v1.2/queryContentList", param);
        } catch (Exception e) {
            return e.getMessage();
        }
        return "working";
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        String authorization = fileBucket.getFieldJson().getString("authorization");
        authorization = cn.hutool.core.codec.Base64.decodeStr(authorization);

        String[] authorizationArr = authorization.split("\\|");

        Date date = new Date(Long.parseLong(authorizationArr[3]));

        LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        LocalDate now = LocalDate.now();

        long daysDiff = ChronoUnit.DAYS.between(now, localDate);
        if (daysDiff > 15) {
            return null;
        }

        String url = "https://aas.caiyun.feixin.10086.cn:443/tellin/authTokenRefresh.do";

        authorizationArr = authorization.split(":");

        HttpResponse response = HttpRequest.post(url)
                .contentType("application/xml")
                .body("<root><token>" + authorizationArr[2] + "</token><account>" + authorizationArr[1] + "</account><clienttype>656</clienttype></root>")
                .execute();

        Document doc = XmlUtil.readXML(response.body());
        Element root = doc.getDocumentElement();

        String token = XmlUtil.elementText(root, "token");

        token = authorizationArr[0] + ":" + authorizationArr[1] + ":" + token;

        token = cn.hutool.core.codec.Base64.encode(token);

        fileBucket.getFieldJson().put("authorization", token);

        return fileBucket;
    }

    private JSONObject request(FileBucket fileBucket, String method, String api, JSONObject requestBody) {
        String url = BASIC_URL + api;

        String bodyStr = "";
        if (requestBody != null) {

            String authorization = fileBucket.getFieldJson().getString("authorization");

            authorization = Base64.decodeStr(authorization);

            String account = authorization.split("\\|")[0].split(":")[1];

            JSONObject commonAccountInfo = new JSONObject();
            commonAccountInfo.put("accountType", 1);
            commonAccountInfo.put("account", account);

            requestBody.put("cloudID", fileBucket.getFieldJson().getString("familyId"));
            requestBody.put("cloudType", 1);
            requestBody.put("commonAccountInfo", commonAccountInfo);

            bodyStr = requestBody.toJSONString();
        }

        String body = HttpUtil.createRequest(Method.valueOf(method), url)
                .header("authorization", "Basic " + fileBucket.getFieldJson().getString("authorization"))
                .body(bodyStr)
                .execute()
                .body();
        JSONObject responseJson = JSONObject.parseObject(body);

        if (!responseJson.getBoolean("success")) {
            throw new RuntimeException(responseJson.getString("message"));
        }

        return responseJson.getJSONObject("data");
    }

    private String getFilePath(FileBucket fileBucket, String uri) throws IOException {
        StringBuilder path = new StringBuilder();
        String queryPath = "";

        if (uri.startsWith("/家庭音乐（移动网盘系统自建）")) {
            FileResource fileResource = getFolderItself(fileBucket, "/家庭音乐（移动网盘系统自建）");
            return fileResource.getId();
        }

        for (int i = 0; i < uri.split("/").length; i++) {
            if (i == uri.split("/").length - 1) {
                break;
            }
            queryPath += "/" + uri.split("/")[i];
            queryPath = PathUtils.normalizePath(queryPath);
            FileResource fileResource = getFolderItself(fileBucket, queryPath);
            if (i != 0) {
                path.append("/");
            }
            path.append(fileResource.getId());
        }

        return path.toString();
    }

    private String getTaskStatus(FileBucket fileBucket, String taskId) {
        JSONObject param = new JSONObject();
        param.put("taskID", taskId);

        JSONObject resp = request(fileBucket, "POST", "/batchOprTask/v1.0/queryBatchOprTaskDetail", param);

        return "";
    }

    private Map<String, String> getHeader(String authorization) {
        Map<String, String> map = new HashMap<>();
        map.put("Authorization", authorization);
        map.put("X-Yun-Api-Version", "v1");
        map.put("X-Yun-app-channel", "10000034");
        map.put("x-yun-client-info", "||9|7.15.4|chrome|138.0.0.0|||macos 10.15.7||zh-CN|||dW5kZWZpbmVk||");
        map.put("Content-Type", "application/json");
        map.put("X-Deviceinfo", "||9|7.16.0|chrome|140.0.0.0|61b2165fba118b2a300b83669c9acea7||macos 10.15.7||zh-CN|||");
        return map;
    }
}
