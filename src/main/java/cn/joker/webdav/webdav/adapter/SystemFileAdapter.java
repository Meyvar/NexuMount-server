package cn.joker.webdav.webdav.adapter;

import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.business.entity.SysSetting;
import cn.joker.webdav.business.service.ISysSettingService;
import cn.joker.webdav.fileTask.TaskManager;
import cn.joker.webdav.fileTask.taskImpl.CopyTask;
import cn.joker.webdav.utils.PathUtils;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@AdapterComponent(title = "系统文件适配器")
public class SystemFileAdapter implements IFileAdapter {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TaskManager taskManager;

    @Autowired
    private ISysSettingService sysSettingService;

    @Override
    public boolean hasPath(FileBucket fileBucket, String path) {
        String filePath = fileBucket.getSourcePath() + path;
        File file = new File(filePath);
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
        if ("/".equals(bucketPath)) {
            bucketPath = "";
        }

        fileResource.setHref(bucketPath + uri);

        String path = bucketPath + uri;
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String[] paths = path.split("/");

        if (paths.length > 0) {
            fileResource.setName(paths[paths.length - 1]);
        }

        return fileResource;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri, boolean refresh) throws IOException {
        if (!uri.endsWith("/")) {
            uri += "/";
        }
        Path filePath = Path.of(fileBucket.getSourcePath() + uri);


        Cache cache = cacheManager.getCache("fileResourceMap");

        Map<String, List<FileResource>> map = cache.get("systemFileAdapter:" + fileBucket.getPath(), Map.class);

        if (map == null) {
            map = new HashMap<>();
        }

        List<FileResource> resourceList = map.get(filePath.toString());

        if (resourceList != null && !resourceList.isEmpty()) {
            if (refresh) {
                map.remove(filePath.toString());
            } else {
                return new ArrayList<>(resourceList);
            }
        }


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
            map.put(filePath.toString(), new ArrayList<>(list));
            cache.put("systemFileAdapter:" + filePath, map);
        }

        return list;
    }

    @Override
    public void get(FileBucket fileBucket, String path) throws IOException {
        HttpServletRequest req = RequestHolder.getRequest();
        HttpServletResponse resp = RequestHolder.getResponse();

        File file = new File(path);
        resp.setStatus(HttpServletResponse.SC_OK);
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

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream out = resp.getOutputStream()) {

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
            }
        } else {
            if (file.isDirectory()) {
                resp.setContentType("application/zip");
                resp.setHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + ".zip\"");
                try {
                    ZipOutputStream zipOut = new ZipOutputStream(resp.getOutputStream());
                    zipFolder(file, file.getName(), zipOut);
                    zipOut.finish();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                resp.setContentLength(Math.toIntExact(file.length()));
                InputStream inputStream = new FileInputStream(file);
                inputStream.transferTo(resp.getOutputStream());
                inputStream.close();
            }
        }
    }

    // 递归压缩目录
    private void zipFolder(File folder, String basePath, ZipOutputStream zipOut) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryName = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                zipFolder(file, entryName, zipOut);
            } else {
                zipOut.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(zipOut);
                }
                zipOut.closeEntry();
            }
        }
    }

    @Override
    public void put(FileBucket fileBucket, String path, Path tempFilePath) throws IOException {

        // 确保父目录存在
        Path targetPath = Paths.get(path);
        if (targetPath.getParent() != null) {
            Files.createDirectories(targetPath.getParent());
            cleanCache(fileBucket.getPath(), Paths.get(path).getParent().toString());
        }

        Files.copy(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void delete(FileBucket fileBucket, String path) throws IOException {

        if (Files.isDirectory(Paths.get(path))) {
            Files.walkFileTree(Paths.get(path), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.delete(Paths.get(path));
        }
        cleanCache(fileBucket.getPath(), Paths.get(path).getParent().toString());
    }

    @Override
    public void mkcol(FileBucket fileBucket, String path) throws IOException {
        Files.createDirectories(Paths.get(path));
        cleanCache(fileBucket.getPath(), Paths.get(path).getParent().toString());
    }

    @Override
    public void move(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            Files.createDirectories(Paths.get(toPath).getParent());
            Files.move(Paths.get(fromPath), Paths.get(toPath));

            cleanCache(fromFileBucket.getPath(), Paths.get(fromPath).getParent().toString());
            cleanCache(fromFileBucket.getPath(), Paths.get(toPath).getParent().toString());
        } else {

        }
    }

    @Override
    public void copy(FileBucket fromFileBucket, String fromPath, FileBucket toFileBucket, String toPath) throws IOException {
        if (fromFileBucket.getUuid().equals(toFileBucket.getUuid())) {
            //同一个存储桶操作
            File sourceFile = Paths.get(fromPath).toFile();
            File destFile = Paths.get(toPath).toFile();
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

            cleanCache(fromFileBucket.getPath(), Paths.get(toPath).getParent().toString());
        } else {
            //夸桶操作

            SysSetting sysSetting = sysSettingService.get();
            String token = StpUtil.getTokenValue();

            String uuid = UUID.randomUUID().toString().replace("-", "");
            CopyTask copyTask = new CopyTask(uuid, fromFileBucket, toFileBucket, fromPath, toPath, sysSetting.getTaskBufferSize());

            taskManager.startTask(uuid, copyTask, token);
        }
    }

    @Override
    public String getDownloadUrl(FileBucket fileBucket, String path) {
        return "/api/pub/dav/load.do?path=" + path + "&token=" + StpUtil.getTokenValue();
    }

    @Override
    public String workStatus(FileBucket fileBucket) {
        return "work";
    }

    private void cleanCache(String path, String id) {
        Cache cache = cacheManager.getCache("fileResourceMap");
        Map<String, List<FileResource>> map = cache.get("systemFileAdapter:" + path, Map.class);
        if (map != null) {
            map.remove(id);
        }
    }
}
