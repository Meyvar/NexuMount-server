package cn.joker.webdav.handle;


import cn.joker.webdav.webdav.entity.FileResource;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class RootHandle implements FileHandle {
    @Override
    public boolean hasPath(Path path) {
        return false;
    }

    @Override
    public List<FileResource> handlePropFind(Path path, String uri) {
        FileResource fileRessource = new FileResource();
        fileRessource.setType("folder");
        fileRessource.setName("本地路径");
        fileRessource.setSize(0L);
        fileRessource.setDate(new Date());
        List<FileResource>  list = new ArrayList<>();
        list.add(fileRessource);
        return list;
    }

    @Override
    public void handleGet(Path path) {

    }

    @Override
    public void handlePut(Path path) {

    }

    @Override
    public void handleDelete(Path path) {

    }

    @Override
    public void handleMkcol(Path path) {

    }

    @Override
    public void handleMove(Path sourcePath) {

    }
}
