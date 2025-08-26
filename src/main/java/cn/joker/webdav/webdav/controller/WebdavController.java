package cn.joker.webdav.webdav.controller;

import cn.joker.webdav.result.Response;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.service.IApiService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/pub/dav")
public class WebdavController {

    @Autowired
    private IApiService apiService;

    @PostMapping("/list.do")
    public Response<List<FileResource>> list(@RequestBody RequestParam param) {
        return Response.success(apiService.list(param));
    }

    @PostMapping("/delete.do")
    public Response<String> delete(RequestParam param) {
        apiService.delete(param);
        return Response.success();
    }

    @PostMapping("/deleteList.do")
    public Response<String> deleteList(@RequestBody List<String> pathList) {
        pathList.forEach(path -> {
            RequestParam param = new RequestParam();
            param.setPath(path);
            apiService.delete(param);
        });
        return Response.success();
    }

    @GetMapping("/get.do")
    public Response<FileResource> get(RequestParam param) {
        return Response.success(apiService.get(param));
    }

    @GetMapping("/load.do")
    public void load(RequestParam param) {
        apiService.load(param);
    }

    @GetMapping("/download.do")
    public void download(RequestParam param) {
        apiService.download(param);
    }

    @GetMapping("/packageDownload.do")
    public void packageDownload(String fileList, String fileName) {
        List<String> list = JSONObject.parseObject(fileList, List.class);
        apiService.packageDownload(list, fileName);
    }

    @PostMapping("/upload.do")
    public Response<String> upload(MultipartFile file, String path, String toPath) {
        apiService.upload(file, path, toPath);
        return Response.success();
    }

    @PostMapping("/createFolder.do")
    public Response<String> createFolder(RequestParam param) {
        apiService.createFolder(param);
        return Response.success();
    }

    @PostMapping("/createFile.do")
    public Response<String> createFile(RequestParam param) {
        apiService.createFile(param);
        return Response.success();
    }

    @PostMapping("/move.do")
    public Response<String> move(RequestParam param) {
        apiService.move(param);
        return Response.success();
    }

    @PostMapping("/moveList.do")
    public Response<String> moveList(@RequestBody List<String> pathList) {
        pathList.forEach(path -> {
            RequestParam param = new RequestParam();
            param.setMethod("moveList");
            param.setPath(path);
            apiService.move(param);
        });
        return Response.success();
    }


    @PostMapping("/copy.do")
    public Response<String> copy(RequestParam param) {
        apiService.copy(param);
        return Response.success();
    }

    @PostMapping("/copyList.do")
    public Response<String> copyList(@RequestBody List<String> pathList) {
        pathList.forEach(path -> {
            RequestParam param = new RequestParam();
            param.setMethod("copyList");
            param.setPath(path);
            apiService.copy(param);
        });
        return Response.success();
    }
}
