package cn.joker.webdav.webdav.adapter;

import cn.hutool.http.HttpRequest;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@AdapterComponent(title = "阿里网盘")
public class AlipanAdapter implements IFileAdapter {

    @Getter
    @Setter
    @ParamAnnotation(label = "accessToken")
    private String accessToken;

    private String BASE_URL = "https://user.aliyundrive.com";

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        return false;
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        return null;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        return List.of();
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
        String url = "https://user.aliyundrive.com/v2/user/get";

        String body = HttpRequest.post(url)
                .contentType("application/json")
                .header("authorization", fileBucket.getFieldJson().getString("accessToken"))
                .body("{}")
                .execute().body();

        JSONObject jsonObject = JSON.parseObject(body);

        if (StringUtils.hasText(jsonObject.getString("user_id"))) {
            return "working";
        } else {
            return "error";
        }
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        return null;
    }
}
