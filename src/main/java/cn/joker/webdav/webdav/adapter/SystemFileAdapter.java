package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.utils.RequestHolder;
import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileResource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@AdapterComponent(title = "系统文件适配器")
public class SystemFileAdapter implements IFileAdapter {

    @Override
    public boolean hasPath(Path path) {
        File file = path.toFile();
        return file.exists();
    }

    @Override
    public FileResource getFolderItself(String path) {
        File file = new File(path);
        FileResource fileResource = new FileResource();
        fileResource.setType("folder");
        fileResource.setSize(0L);
        fileResource.setDate(new Date(file.lastModified()));
        fileResource.setHref(path);

        path = path.substring(0, path.length() - 1);

        String[] paths = path.split("/");

        if (paths.length > 0) {
            fileResource.setName(paths[paths.length - 1]);
        }

        return fileResource;
    }

    @Override
    public List<FileResource> propFind(String path, String uri) {

//        if (!hasPath(Path.of(path + uri))) {
//            HttpServletResponse response = RequestHolder.getResponse();
//            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
//            return null;
//        }

        Path filePath = Path.of(path + uri);
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
                ressource.setHref(path + uri + f.getName());
                list.add(ressource);
            }
        }

        return list;
    }

    @Override
    public void get(Path path) {

    }

    @Override
    public void put(Path path) {

    }

    @Override
    public void delete(Path path) {

    }

    @Override
    public void mkcol(Path path) {

    }

    @Override
    public void move(Path sourcePath) {

    }
}
