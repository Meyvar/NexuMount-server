package cn.joker.webdav.business.controller;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.IFileBucketService;
import cn.joker.webdav.result.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static cn.joker.webdav.result.Response.success;

@RestController
@RequestMapping("/api/fileBucket")
public class FileBucketController {

    @Autowired
    private IFileBucketService fileBucketService;


    @RequestMapping("/list.do")
    public Response<List<FileBucket>> list() {
        return success(fileBucketService.findAll());
    }

    @RequestMapping("/getFileAdapterList.do")
    public Response<List<Map<String, String>>> getFileAdapterList() {
        return success(fileBucketService.getFileAdapterList());
    }

    @RequestMapping("/save.do")
    public Response<String> save(FileBucket fileBucket) {
        fileBucketService.save(fileBucket);
        return success();
    }
}
