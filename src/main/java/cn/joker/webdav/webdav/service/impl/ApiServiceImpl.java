package cn.joker.webdav.webdav.service.impl;

import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.entity.RequestStatus;
import cn.joker.webdav.webdav.service.IApiService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class ApiServiceImpl implements IApiService {

    private AdapterManager getAdapterManager(RequestParam param) {
        return new AdapterManager(param.getPath());
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
            if ("/".equals(param.getPath())) {
                resource.setHref(param.getPath() + resource.getHref());
            }
            if (resource.getType().equals("folder")) {

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
            resource.setHref(adapterManager.getDownloadUrl());
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
}
