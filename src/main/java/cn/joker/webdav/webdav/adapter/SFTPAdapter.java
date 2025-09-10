package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.fileUpload.UploadInputStream;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.entity.FileResource;
import com.jcraft.jsch.*;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@AdapterComponent(title = "SFTP")
public class SFTPAdapter implements IFileAdapter {

    @ParamAnnotation(label = "服务器地址")
    private String url;

    @ParamAnnotation(label = "服务器端口号")
    private String port;

    @ParamAnnotation(label = "用户名")
    private String username;

    @ParamAnnotation(label = "密码")
    private String password;


    @Autowired
    private FilePathCacheService filePathCacheService;

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        if (path.equals("/")) {
            return true;
        }
        try {
            FileResource fileResource = getFolderItself(fileBucket, path);
            if (fileResource != null) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        String name = Paths.get(uri).getFileName().toString();

        List<FileResource> list = propFind(fileBucket, PathUtils.toLinuxPath(Paths.get(uri).getParent()), false);

        for (FileResource resource : list) {
            if (name.equals(resource.getName())) {
                return resource;
            }
        }
        return null;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        List<FileResource> list = filePathCacheService.get(fileBucket.getUuid() + fileBucket.getPath() + uri);

        if (list != null && !list.isEmpty()) {
            return list;
        }

        list = new ArrayList<>();


        List<FileResource> finalList = list;
        connect(fileBucket, (session, channelSftp) -> {
            Vector<ChannelSftp.LsEntry> vector = channelSftp.ls(uri);
            for (ChannelSftp.LsEntry entry : vector) {
                if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) {
                    continue;
                }
                FileResource resource = new FileResource();
                resource.setName(entry.getFilename());
                resource.setType(entry.getAttrs().isDir() ? "folder" : "file");
                resource.setSize(entry.getAttrs().getSize());
                resource.setDate(new Date(entry.getAttrs().getMTime() * 1000L));
                resource.setContentType(URLConnection.guessContentTypeFromName(resource.getName()));
                finalList.add(resource);
            }
        });


        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);
        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        String range = request.getHeader("Range");


        connect(fileBucket, (session, channelSftp) -> {
            SftpATTRS attrs = channelSftp.lstat(path);
            response.setContentType(URLConnection.guessContentTypeFromName(path));
            long fileSize = attrs.getSize();

            long start = 0, end = fileSize - 1;
            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.replace("bytes=", "").split("-");
                start = Long.parseLong(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1]);
                }
            }

            long contentLength = end - start + 1;

            // 设置响应头
            response.setStatus(range != null ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Length", String.valueOf(contentLength));
            if (range != null) {
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);
            }

            // 打开远程输入流，从 start 开始读取
            try (InputStream inputStream = channelSftp.get(path, null, start);
                 ServletOutputStream outputStream = response.getOutputStream()) {

                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                int len;
                while (remaining > 0 && (len = inputStream.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                    outputStream.write(buffer, 0, len);
                    remaining -= len;
                }
                outputStream.flush();
            }
        });
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {
        connect(fileBucket, (session, channelSftp) -> {
            channelSftp.put(new UploadInputStream(tempFilePath.toFile(), hook, tempFilePath.toFile().length(), 0), path);
        });
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        connect(fileBucket, (session, channelSftp) -> {
            FileResource fileResource = getFolderItself(fileBucket, path);
            if (fileResource == null) {
                return;
            }
            if (fileResource.getType().equals("folder")) {
                channelSftp.rmdir(path);
            } else {
                channelSftp.rm(path);
            }
        });
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        connect(fileBucket, (session, channelSftp) -> {
            channelSftp.mkdir(path);
        });
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        connect(fromFileBucket, (session, channelSftp) -> {
            channelSftp.rename(fromPath, toPath);
        });
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        connect(fromFileBucket, (session, channelSftp) -> {
            ChannelExec exec = null;
            try {
                exec = (ChannelExec) session.openChannel("exec");
                exec.setCommand("cp " + fromPath + " " + toPath);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            } finally {
                if (exec != null) {
                    exec.connect();
                    exec.disconnect();
                }
            }
        });
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        header.put("path", path);
        return "SFTP";
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        AtomicReference<String> status = new AtomicReference<>("error");
        connect(fileBucket, (session, channelSftp) -> {
            channelSftp.lstat(".");
            status.set("working");
        });
        return status.get();
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        return null;
    }


    public void connect(FileBucket fileBucket, CollBack collBack) {
        Session session = null;
        ChannelSftp channelSftp = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(fileBucket.getFieldJson().getString("username"), fileBucket.getFieldJson().getString("url"), fileBucket.getFieldJson().getInteger("port"));
            session.setPassword(fileBucket.getFieldJson().getString("password"));
            session.setConfig("StrictHostKeyChecking", "no"); // 忽略 host key 检查
            session.connect(10000); // 超时10秒

            Channel channel = session.openChannel("sftp");
            channel.connect();

            channelSftp = (ChannelSftp) channel;

            collBack.collBack(session, channelSftp);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session.isConnected()) {
                session.disconnect();
            }
        }
    }

    public interface CollBack {
        void collBack(Session session, ChannelSftp channelSftp) throws Exception;
    }
}
