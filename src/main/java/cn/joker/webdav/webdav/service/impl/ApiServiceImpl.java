package cn.joker.webdav.webdav.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.ISysUserService;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.entity.RequestStatus;
import cn.joker.webdav.webdav.service.IApiService;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

//        param.setPath(URLDecoder.decode(param.getPath(), StandardCharsets.UTF_8));

        return new AdapterManager(param.getPath(), userPath);
    }

    @Override
    public List<FileResource> list(RequestParam param) {
        RequestStatus status = null;
        try {
            status = getAdapterManager(param).propFind(param.isRefresh());
            if (!status.isSuccess()) {
                throw new RuntimeException("文件不存在");
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
        FileResource fileResource = get(param);

        String[] paths = param.getPath().split("/");
        String fileName = paths[paths.length - 1];
        if (fileResource.getType().equals("folder")) {
            fileName += ".zip";
            fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            packageDownload(Arrays.asList(param.getPath()), fileName);
        } else {
            fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            HttpServletResponse response = RequestHolder.getResponse();
            response.setHeader("Content-disposition", "attachment; filename=" + fileName);
            load(param);
        }
    }

    @Override
    public void packageDownload(List<String> list, String fileName) {
        if (list.isEmpty()) {
            throw new RuntimeException("下载文件不能为空！");
        }
        HttpServletResponse response = RequestHolder.getResponse();
        response.setHeader("Content-disposition", "attachment; filename=" + fileName);
        response.setContentType("application/zip");

        List<String> fileList = new ArrayList<>();

        for (String s : list) {
            RequestParam param = new RequestParam();
            param.setPath(s);
            FileResource resource = get(param);
            if (resource.getType().equals("file")) {
                fileList.add(resource.getHref());
            } else {
                fileList.addAll(getAllFile(s));
            }
        }
        String commonPrefix = "";
        if (list.size() > 1) {
            commonPrefix = getCommonPrefix(fileList);
        }


        Map<String, String> fileMap = new HashMap<>();
        for (String s : fileList) {
            String path = PathUtils.normalizePath("/" + s.replace(commonPrefix, ""));
            String url = RequestHolder.getRequest().getRequestURL().toString();
            url = url.replace("packageDownload", "download");
            fileMap.put(path, url + "?path=" + URLEncoder.encode(s, StandardCharsets.UTF_8) + "&token=" + StpUtil.getTokenValue());
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(response.getOutputStream())) {

            for (Map.Entry<String, String> entry : fileMap.entrySet()) {
                String zipPath = entry.getKey(); // 文件在压缩包中的路径
                String fileUrl = entry.getValue(); // 下载地址

                // 创建 ZIP 条目
                ZipEntry zipEntry = new ZipEntry(zipPath.startsWith("/") ? zipPath.substring(1) : zipPath);
                zipOut.putNextEntry(zipEntry);

                // 下载文件并写入 ZIP
                URL url = new URL(fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                try (InputStream in = conn.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, len);
                    }
                }

                zipOut.closeEntry();
            }

            zipOut.finish();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static String getCommonPrefix(List<String> paths) {
        if (paths == null || paths.isEmpty()) return "";

        // 以第一个路径为初始前缀
        String prefix = paths.get(0);
        for (int i = 1; i < paths.size(); i++) {
            prefix = commonPrefix(prefix, paths.get(i));
            if (prefix.isEmpty()) break; // 没有公共前缀
        }
        return prefix;
    }

    private static String commonPrefix(String s1, String s2) {
        int minLen = Math.min(s1.length(), s2.length());
        int i = 0;
        while (i < minLen && s1.charAt(i) == s2.charAt(i)) {
            i++;
        }
        // 去掉末尾可能不完整的文件夹名，保留到最后一个 "/"
        int lastSlash = s1.substring(0, i).lastIndexOf('/');
        return lastSlash >= 0 ? s1.substring(0, lastSlash + 1) : "";
    }

    private List<String> getAllFile(String path) {
        List<String> fileList = new ArrayList<>();
        RequestParam param = new RequestParam();
        param.setPath(path);
        List<FileResource> list = list(param);
        for (FileResource fileResource : list) {
            if (fileResource.getType().equals("folder")) {
                fileList.addAll(getAllFile(fileResource.getHref()));
            } else {
                fileList.add(fileResource.getHref());
            }
        }
        return fileList;
    }

    @Override
    public void upload(MultipartFile file, String path, String toPath) {
        RequestParam param = new RequestParam();
        param.setPath(path + "/" + file.getOriginalFilename());
        AdapterManager adapterManager = getAdapterManager(param);
        try {
            adapterManager.put(file.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
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

        if (StringUtils.hasText(param.getMethod()) && "moveList".equals(param.getMethod())) {
            Path path = Paths.get(param.getPath());
            destPathRaw += "/" + path.getFileName();
            destPathRaw = PathUtils.normalizePath(destPathRaw);
        }

        RequestParam toParam = new RequestParam();

        toParam.setPath(destPathRaw);

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


        if (StringUtils.hasText(param.getMethod()) && "copyList".equals(param.getMethod())) {
            Path path = Paths.get(param.getPath());
            destPathRaw += "/" + path.getFileName();
            destPathRaw = PathUtils.normalizePath(destPathRaw);
        }
        RequestParam toParam = new RequestParam();
        toParam.setPath(destPathRaw);

        AdapterManager toAdapterManager = getAdapterManager(toParam);

        try {
            adapterManager.copy(toAdapterManager);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
