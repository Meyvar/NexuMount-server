package cn.joker.webdav.webdav.adapter;

import cn.joker.webdav.webdav.adapter.contract.AdapterComponent;
import cn.joker.webdav.webdav.adapter.contract.IFileAdapter;
import cn.joker.webdav.webdav.entity.FileRessource;

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
        return false;
    }

    @Override
    public FileRessource getFolderItself(String path) {
        File file = new File(path);
        FileRessource fileRessource = new FileRessource();
        fileRessource.setType("folder");
        fileRessource.setName(path);
        fileRessource.setSize(0L);
        fileRessource.setDate(new Date(file.lastModified()));
        return fileRessource;
    }

    @Override
    public List<FileRessource> propFind(String path, String uri) {
        Path filePath = Path.of(path + uri);
        File file = filePath.toFile();
        File[] files = file.isDirectory() ? file.listFiles() : new File[0];

        List<FileRessource> list = new ArrayList<>();

        if (files != null) {
            for (File f : files) {
                FileRessource ressource = new FileRessource();

                ressource.setType(f.isDirectory() ? "folder" : "file");
                ressource.setName(f.getName());
                try {
                    ressource.setDate(new Date(Files.getLastModifiedTime(f.toPath()).toMillis()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                ressource.setSize(f.isDirectory() ? 0L : f.length());

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
