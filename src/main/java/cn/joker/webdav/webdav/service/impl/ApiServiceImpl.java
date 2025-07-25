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

        return new AdapterManager(param.getPath(), userPath);
    }

    @Override
    public List<FileResource> list(RequestParam param) {
        RequestStatus status = null;
        try {
            status = getAdapterManager(param).propFind();
            if (!status.isSuccess()) {
                throw new RuntimeException(status.getMessage());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
            throw new RuntimeException(e);
        }
    }

    @Override
    public FileResource get(RequestParam param) {
        FileResource resource;
        try {
            AdapterManager adapterManager = getAdapterManager(param);
            resource = adapterManager.getFolderItself();
            resource.setHref(adapterManager.getDownloadUrl(resource.getContentType()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return resource;
    }

    @Override
    public void load(RequestParam param) {
        try {
            HttpServletResponse resp = RequestHolder.getResponse();
            HttpServletRequest req = RequestHolder.getRequest();

            GetFileResource fileResource = getAdapterManager(param).get();
            resp.setContentLength(Math.toIntExact(fileResource.getFileSize()));

            File file = new File(fileResource.getFilePath());

            resp.setContentType(Files.probeContentType(file.toPath()));


            String rangeHeader = req.getHeader("Range");
            if (StringUtils.hasText(rangeHeader)) {
                String[] ranges = rangeHeader.replace("bytes=", "").split("-");
                long start = Long.parseLong(ranges[0]);
                long end = (ranges.length > 1 && !ranges[1].isEmpty())
                        ? Long.parseLong(ranges[1])
                        : file.length() - 1;

                long contentLength = end - start + 1;

                resp.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                resp.setHeader("Content-Type", Files.probeContentType(file.toPath()));
                resp.setHeader("Accept-Ranges", "bytes");
                resp.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + file.length());
                resp.setHeader("Content-Length", String.valueOf(contentLength));

                RandomAccessFile raf = new RandomAccessFile(file, "r");
                OutputStream out = resp.getOutputStream();

                raf.seek(start);

                byte[] buffer = new byte[8192];
                long remaining = contentLength;

                while (remaining > 0) {
                    int read = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read == -1) {
                        break;
                    }
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
                raf.close();
                out.flush();

            } else {
                InputStream inputStream = new FileInputStream(file);
                inputStream.transferTo(resp.getOutputStream());
                inputStream.close();
            }
        } catch (IOException e) {
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
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createFolder(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.mkcol();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createFile(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.put(RequestHolder.getRequest().getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void move(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.move();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copy(RequestParam param) {
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.copy();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
