package cn.joker.webdav.webdav.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.cache.FilePathCacheService;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.UploadHook;
import cn.joker.webdav.fileTask.taskImpl.CopyTask;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.utils.fileUpload.UploadInputStream;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.adapter.contract.ParamAnnotation;
import cn.joker.webdav.webdav.adapter.contract.ParamOption;
import cn.joker.webdav.webdav.entity.FileResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.file.remote.InputStreamCallback;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.ftp.session.AbstractFtpSessionFactory;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.integration.ftp.session.DefaultFtpsSessionFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@AdapterComponent(title = "FTP/FTPS")
public class FtpAdapter implements IFileAdapter {

    @ParamAnnotation(label = "FTP类型", type = "el-select", options = {
            @ParamOption(key = "FTP", value = "ftp"),
            @ParamOption(key = "FTPS", value = "ftps"),
    })
    private String ftpType;

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

    @Autowired
    private TaskManager taskManager;

    @Autowired
    private ISysSettingService sysSettingService;


    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        List<FileResource> list = filePathCacheService.get(fileBucket.getUuid() + fileBucket.getPath() + uri);

        if (list != null && !list.isEmpty()) {
            return list;
        }

        list = new ArrayList<>();

        FTPFile[] files = getFtpRemoteFileTemplate(fileBucket).list(uri);
        for (FTPFile file : files) {
            FileResource fileResource = new FileResource();
            fileResource.setName(file.getName());
            fileResource.setType(file.isDirectory() ? "folder" : "file");
            fileResource.setSize(file.getSize());
            fileResource.setDate(file.getTimestamp().getTime());
            fileResource.setContentType(URLConnection.guessContentTypeFromName(fileResource.getName()));
            list.add(fileResource);
        }

        filePathCacheService.put(fileBucket.getUuid() + fileBucket.getPath() + uri, list);
        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws Exception {
        FileResource fileResource = getFolderItself(fileBucket, path);

        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();
        response.setContentType(URLConnection.guessContentTypeFromName(Paths.get(path).getFileName().toString()));

        long fileLength = fileResource.getSize();

        String range = request.getHeader("Range");

        if (StringUtils.hasText(range) && range.startsWith("bytes=")) {
            String[] ranges = range.replace("bytes=", "").split("-");
            long start = Long.parseLong(ranges[0]);
            long end = (ranges.length > 1 && !ranges[1].isEmpty())
                    ? Long.parseLong(ranges[1])
                    : fileResource.getSize() - 1;

            long contentLength = end - start + 1;

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setHeader("Content-Type", fileResource.getContentType());
            response.setHeader("Accept-Ranges", "bytes");
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileResource.getSize());
            response.setHeader("Content-Length", String.valueOf(contentLength));

            getFtpRemoteFileTemplate(fileBucket).execute(session -> {
                FTPClient ftpClient = (FTPClient) session.getClientInstance();
                ftpClient.setRestartOffset(start);
                try (var inputStream = ftpClient.retrieveFileStream(path);
                     var os = response.getOutputStream()) {

                    // 如果指定了 end，需要限制读取长度
                    long bytesToRead = end > 0 ? end - start + 1 : Long.MAX_VALUE;
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = inputStream.read(buffer)) != -1 && bytesToRead > 0) {
                        if (len > bytesToRead) len = (int) bytesToRead;
                        os.write(buffer, 0, len);
                        bytesToRead -= len;
                    }
                }
                ftpClient.completePendingCommand(); // 完成 FTP 下载
                return null;
            });


        } else {
            getFtpRemoteFileTemplate(fileBucket).get(path, new InputStreamCallback() {
                @Override
                public void doWithInputStream(InputStream stream) throws IOException {
                    response.setContentLength((int) fileLength);
                    stream.transferTo(RequestHolder.getResponse().getOutputStream());
                }
            });
        }
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath, UploadHook hook) throws Exception {
        getFtpRemoteFileTemplate(fileBucket).execute(session -> {
            session.write(new UploadInputStream(tempFilePath.toFile(), hook, tempFilePath.toFile().length(), 0), path);
            return null;
        });
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {
        if (hasPath(fileBucket, path)) {
            getFtpRemoteFileTemplate(fileBucket).remove(path);
        }
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        getFtpRemoteFileTemplate(fileBucket).execute(session -> {
            FTPClient ftpClient = (FTPClient) session.getClientInstance();
            ftpClient.makeDirectory(path);
            return null;
        });
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        getFtpRemoteFileTemplate(fromFileBucket).rename(fromPath, toPath);
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSettingService.get().getTaskBufferSize());

        taskManager.startTask(uuid, copyTask, StpUtil.getTokenValue());
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path, Map<String, String> header) throws IOException {
        header.put("path", path);
        return "FTP";
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        try {
            boolean success = getFtpRemoteFileTemplate(fileBucket).execute(session -> true);
            return success ? "working" : "error";
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    @Override
    public FileBucket refreshToken(FileBucket fileBucket) {
        return null;
    }

    private static Map<String, RemoteFileTemplate<FTPFile>> remoteFileTemplateMap = new HashMap<>();

    public static RemoteFileTemplate<FTPFile> getFtpRemoteFileTemplate(FileBucket fileBucket) {

        RemoteFileTemplate<FTPFile> remoteFileTemplate = remoteFileTemplateMap.get(fileBucket.getUuid());

        if (remoteFileTemplate != null) {
            return remoteFileTemplate;
        }

        AbstractFtpSessionFactory<?> factory;

        if (fileBucket.getFieldJson().getString("ftpType").equals("ftps")) {
            DefaultFtpsSessionFactory ftpsSessionFactory = new DefaultFtpsSessionFactory();

            ftpsSessionFactory.setUseClientMode(true); // 客户端模式
            ftpsSessionFactory.setProtocol("TLS");     // 支持 TLS 或 SSL
            ftpsSessionFactory.setImplicit(false);     // 显式 FTPS (AUTH TLS)；如果是 Implicit FTPS 就设 true

            factory = ftpsSessionFactory;
        } else {
            factory = new DefaultFtpSessionFactory();
        }


        factory.setHost(fileBucket.getFieldJson().getString("url"));
        factory.setPort(fileBucket.getFieldJson().getInteger("port"));
        factory.setUsername(fileBucket.getFieldJson().getString("username"));
        factory.setPassword(fileBucket.getFieldJson().getString("password"));
        factory.setClientMode(2);

        // ===== Step 1: 临时会话检测编码 =====
        DefaultFtpSessionFactory tempFactory = new DefaultFtpSessionFactory();
        tempFactory.setHost(fileBucket.getFieldJson().getString("url"));
        tempFactory.setPort(fileBucket.getFieldJson().getInteger("port"));
        tempFactory.setUsername(fileBucket.getFieldJson().getString("username"));
        tempFactory.setPassword(fileBucket.getFieldJson().getString("password"));
        tempFactory.setClientMode(2);

        String encoding = fileBucket.getFieldJson().getString("encoding");
        if (!StringUtils.hasText(encoding)) {
            try (var tempSession = new CachingSessionFactory<>(tempFactory).getSession()) {
                FTPClient ftpClient = (FTPClient) tempSession.getClientInstance();
                int reply = ftpClient.sendCommand("OPTS UTF8 ON");
                encoding = FTPReply.isPositiveCompletion(reply) ? "UTF-8" : "GBK";
                fileBucket.getFieldJson().put("encoding", encoding);
            } catch (IOException e) {
                e.printStackTrace();
                encoding = "GBK";
            }
        }
        // ===== Step 2: 在 factory 上设置编码 =====
        factory.setControlEncoding(encoding);
        remoteFileTemplate = new RemoteFileTemplate<>(new CachingSessionFactory<>(factory));

        remoteFileTemplateMap.put(fileBucket.getUuid(), remoteFileTemplate);

        return remoteFileTemplate;
    }
}
