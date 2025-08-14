package cn.joker.webdav.webdav.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.ISysUserService;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.entity.RequestStatus;
import cn.joker.webdav.webdav.service.IApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ApiServiceImpl implements IApiService {

    @Autowired
    private ISysUserService sysUserService;

    private String userPath;

    private AdapterManager getAdapterManager(RequestParam param) {

        SysUser sysUser = sysUserService.getById(StpUtil.getLoginId().toString());

        userPath = sysUser.getRootPath();

        if ("/".equals(userPath)) {
            userPath = "";
        }

        param.setPath(URLDecoder.decode(param.getPath(), StandardCharsets.UTF_8));

        return new AdapterManager(param.getPath(), userPath);
    }

    @Override
    public List<FileResource> list(RequestParam param) {
        RequestStatus status = null;
        try {
            status = getAdapterManager(param).propFind(param.isRefresh());
            if (!status.isSuccess()) {
                throw new RuntimeException(status.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        List<FileResource> list = status.getFileResources();
        for (FileResource resource : list) {
            if ("/".equals(param.getPath()) && !resource.getHref().startsWith("/")) {
                resource.setHref(param.getPath() + resource.getHref());
            }
        }

        return list;
    }

    @Override
    public void delete(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            RequestStatus status = adapterManager.delete();
            if (!status.isSuccess()) {
                throw new RuntimeException(status.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public FileResource get(RequestParam param) {
        FileResource resource;
        try {
            AdapterManager adapterManager = getAdapterManager(param);
            resource = adapterManager.getFolderItself();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return resource;
    }

    @Override
    public void load(RequestParam param) {
        try {
            getAdapterManager(param).get();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void download(RequestParam param) {
        String[] paths = param.getPath().split("/");
        String fileName = paths[paths.length - 1];
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        HttpServletResponse response = RequestHolder.getResponse();
        response.setHeader("Content-disposition", "attachment; filename=" + fileName);
        load(param);
    }

    @Override
    public void upload(MultipartFile file, String path, String toPath) {
        RequestParam param = new RequestParam();
        param.setPath(path + "/" + file.getOriginalFilename());
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.put(file.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void createFolder(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.mkcol();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void createFile(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.put(RequestHolder.getRequest().getInputStream());
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void move(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);

        HttpServletRequest request = RequestHolder.getRequest();
        String destHeader = request.getHeader("Destination");
        if (destHeader == null) {
            throw new RuntimeException("目标地址不能为空！");
        }
        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        RequestParam toParam = new RequestParam();;
        Path path = Paths.get(param.getPath());
        toParam.setPath(destPathRaw + path.getFileName());

        AdapterManager toAdapterManager = getAdapterManager(toParam);

        try {
            adapterManager.move(toAdapterManager);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public void copy(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);


        HttpServletRequest request = RequestHolder.getRequest();
        String destHeader = request.getHeader("Destination");

        if (destHeader == null) {
           throw new RuntimeException("目标地址不能为空！");
        }

        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        RequestParam toParam = new RequestParam();;
        Path path = Paths.get(param.getPath());
        toParam.setPath(destPathRaw + path.getFileName());

        AdapterManager toAdapterManager = getAdapterManager(toParam);

        try {
            adapterManager.copy(toAdapterManager);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
