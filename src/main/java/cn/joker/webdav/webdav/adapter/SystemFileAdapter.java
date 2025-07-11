package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@AdapterComponent(title = "系统文件适配器")
public class SystemFileAdapter implements IFileAdapter {

    @Override
    public boolean hasPath(String path) {
        File file = new File(path);
        return file.exists();
    }

    @Override
    public FileResource getFolderItself(FileBucket fileBucket, String uri) throws IOException {
        File file = new File(fileBucket.getSourcePath() + uri);
        FileResource fileResource = new FileResource();

        if (file.isDirectory()) {
            fileResource.setType("folder");
            fileResource.setSize(0L);
        } else {
            fileResource.setType("file");
            fileResource.setSize(file.length());
            fileResource.setContentType(Files.probeContentType(file.toPath()));
        }


        fileResource.setDate(new Date(file.lastModified()));

        String bucketPath = fileBucket.getPath();
        if ("/".equals(bucketPath)){
            bucketPath = "";
        }

        fileResource.setHref(bucketPath + uri);

        String path = bucketPath + uri;
        if (path.endsWith("/")){
            path = path.substring(0, path.length() - 1);
        }

        String[] paths = path.split("/");

        if (paths.length > 0) {
            fileResource.setName(paths[paths.length - 1]);
        }

        return fileResource;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri) throws IOException {
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        Path filePath = Path.of(fileBucket.getSourcePath() + uri);
        File file = filePath.toFile();
        File[] files = file.isDirectory() ? file.listFiles() : new File[0];

        List<FileResource> list = new ArrayList<>();

        if (files != null) {
            if (fileBucket.getPath().equals("/") && uri.startsWith("/")) {
                uri = uri.replaceFirst("/", "");
            }
            for (File f : files) {
                FileResource ressource = new FileResource();

                if (f.isDirectory()) {
                    ressource.setType("folder");
                    ressource.setSize(0L);
                } else {
                    ressource.setType("file");
                    ressource.setContentType(Files.probeContentType(f.toPath()));
                    ressource.setSize(f.length());
                }

                ressource.setName(f.getName());
                try {
                    ressource.setDate(new Date(Files.getLastModifiedTime(f.toPath()).toMillis()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                ressource.setHref(fileBucket.getPath() + uri + f.getName());
                list.add(ressource);
            }
        }

        return list;
    }

    @Override
    public GetFileResource get(String path) throws IOException {
        GetFileResource resource = new GetFileResource();
        File file = new File(path);
        resource.setFilePath(file.getPath());
        resource.setFileSize(file.length());
        return resource;
    }

    @Override
    public void put(String path, InputStream input) throws IOException {
        HttpServletRequest req = RequestHolder.getRequest();
        long contentLength = req.getContentLengthLong();
        System.out.println("PUT: " + path + ", content-length: " + contentLength);

        String mTime = req.getHeader("X-Oc-Mtime");
        if (!StringUtils.hasText(mTime)) {
            mTime = req.getHeader("Last-Modified");
        }

        // 确保父目录存在
        Path targetPath = Paths.get(path);
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
        }

        try (OutputStream output = Files.newOutputStream(targetPath)) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }

            output.flush();

            if (StringUtils.hasText(mTime)) {
                long timestamp = Long.parseLong(mTime);
                FileTime fileTime = FileTime.from(Instant.ofEpochSecond(timestamp));
                Files.setLastModifiedTime(targetPath, fileTime);
            }
        } catch (ClientAbortException e) {
            Files.deleteIfExists(targetPath);

        }
    }

    @Override
    public void delete(String path) throws IOException {
        Files.delete(Paths.get(path));
    }

    @Override
    public void mkcol(String path) throws IOException {
        Files.createDirectories(Paths.get(path));
    }

    @Override
    public void move(String sourcePath, String destPath) throws IOException {
        Files.createDirectories(Paths.get(destPath).getParent());
        Files.move(Paths.get(sourcePath), Paths.get(destPath));
    }

    @Override
    public void copy(String sourcePath, String destPath) throws IOException {
        File sourceFile = Paths.get(sourcePath).toFile();
        File destFile = Paths.get(destPath).toFile();
        if (sourceFile.isDirectory()) {
            Files.walk(sourceFile.toPath()).forEach(source -> {
                try {
                    Path target = destFile.toPath().resolve(sourceFile.toPath().relativize(source));
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            Files.createDirectories(destFile.getParentFile().toPath());
            Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public String getDownloadUrl(String path, String fileType) {
        return "/api/pub/dav/load.do?path=" + path;
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        return "work";
    }
}
