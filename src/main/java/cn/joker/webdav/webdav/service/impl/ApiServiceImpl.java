package cn.joker.webdav.webdav.service.impl;

import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.entity.RequestStatus;
import cn.joker.webdav.webdav.service.IApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ApiServiceImpl implements IApiService {

    private AdapterManager getAdapterManager(RequestParam  param) {
        HttpServletRequest request = RequestHolder.getRequest();

        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        Path path = Paths.get(uri).normalize();
        return new AdapterManager(path, uri);
    }

    @Override
    public List<FileResource> list(RequestParam  param) {
        RequestStatus status = null;
        try {
            status = getAdapterManager(param).propFind();
            if (!status.isSuccess()){
                throw new RuntimeException(status.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return status.getFileResources();
    }

    @Override
    public void delete(RequestParam param) {
        try {
            RequestStatus status = getAdapterManager(param).delete();
            if (!status.isSuccess()){
                throw new RuntimeException(status.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
