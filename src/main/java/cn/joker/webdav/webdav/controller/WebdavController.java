package cn.joker.webdav.webdav.controller;

import cn.joker.webdav.result.Response;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.service.IApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pub/dav")
public class WebdavController {

    @Autowired
    private IApiService apiService;

    @PostMapping("/list.do")
    public Response<List<FileResource>> list(RequestParam param) {
        return Response.success(apiService.list(param));
    }

    @GetMapping("/delete.do")
    public Response<String> delete(RequestParam param) {
        apiService.delete(param);
        return Response.success();
    }

}
