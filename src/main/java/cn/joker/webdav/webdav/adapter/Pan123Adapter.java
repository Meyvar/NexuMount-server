package cn.joker.webdav.webdav.adapter;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@AdapterComponent(title = "123云盘")
public class Pan123Adapter implements IFileAdapter {

    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    @ParamAnnotation(label = "refreshToken")
    private String refreshToken;

    private final static String BASIC_URL = "https://open-api.123pan.com";


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
        JSONObject param = new JSONObject();

        FileResource thisFile = getFolderItself(fileBucket, uri);

        List<FileResource> list = new ArrayList<>();

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        int lastFileId = 0;
        while (true) {
            param.put("parentFileId", thisFile.getId());
            param.put("limit", 100);
            if (lastFileId != 0) {
                param.put("lastFileId", lastFileId);
            }

            JSONObject resp = request(fileBucket, "GET", "/api/v2/file/list", null);

            JSONArray fileList = resp.getJSONArray("fileList");

            for (int i = 0; i < fileList.size(); i++) {
                JSONObject file = fileList.getJSONObject(i);
                FileResource fileResource = new FileResource();

                fileResource.setId(file.getString("fileId"));
                fileResource.setName(file.getString("filename"));
                fileResource.setSize(file.getLong("size"));
                fileResource.setType(file.getInteger("type") == 0 ? "folder" : "file");
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
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        return "";
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        return "";
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
