package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.business.entity.FileBucket;
import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public FileResource getFolderItself(FileBucket fileBucket, String uri) {
        File file = new File(fileBucket.getSourcePath() + uri);
        FileResource fileResource = new FileResource();

        if (file.isDirectory()) {
            fileResource.setType("folder");
            fileResource.setSize(0L);
        } else {
            fileResource.setType("file");
            fileResource.setSize(file.length());
        }


        fileResource.setDate(new Date(file.lastModified()));
        fileResource.setHref(fileBucket.getPath() + uri);

        String path = fileBucket.getPath() + uri;
        path = path.substring(0, path.length() - 1);

        String[] paths = path.split("/");

        if (paths.length > 0) {
            fileResource.setName(paths[paths.length - 1]);
        }

        return fileResource;
    }

    @Override
    public List<FileResource> propFind(FileBucket fileBucket, String uri) {

//        if (!hasPath(Path.of(path + uri))) {
//            HttpServletResponse response = RequestHolder.getResponse();
//            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
//            return null;
//        }

        Path filePath = Path.of(fileBucket.getSourcePath() + uri);
        File file = filePath.toFile();
        File[] files = file.isDirectory() ? file.listFiles() : new File[0];

        List<FileResource> list = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                FileResource ressource = new FileResource();

                ressource.setType(f.isDirectory() ? "folder" : "file");
                ressource.setName(f.getName());
                try {
                    ressource.setDate(new Date(Files.getLastModifiedTime(f.toPath()).toMillis()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                ressource.setSize(f.isDirectory() ? 0L : f.length());
                ressource.setHref(fileBucket.getPath() + uri + f.getName());
                list.add(ressource);
            }
        }

        return list;
    }

    @Override
    public InputStream get(String path) throws IOException {
        Path p = Paths.get(path);
        InputStream in = Files.newInputStream(p);
        return in;
    }

    @Override
    public void put(String path) throws IOException {
        HttpServletRequest req = RequestHolder.getRequest();
        long contentLength = req.getContentLengthLong();
        System.out.println("PUT: " + path + ", content-length: " + contentLength);

        Files.createDirectories(Paths.get(path).getParent());

        try (ServletInputStream input = req.getInputStream();
             OutputStream output = Files.newOutputStream(Paths.get(path))) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            long total = 0;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                total += bytesRead;
            }

            output.flush();
            System.out.println("Wrote " + total + " bytes to file.");
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
    public void move(Path sourcePath) {

    }
}
