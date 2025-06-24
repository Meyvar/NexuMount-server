package cn.joker.webdav.handle;

import cn.joker.webdav.business.entity.FileRessource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Service
public class SystemFileHandle implements FileHandle {
    @Override
    public boolean hasPath(Path path) {

        String adapter = "/webdav";


        // 要替换的原始前缀路径
        Path oldPrefix = path;

        // 新的前缀路径
        Path newPrefix = Paths.get(adapter);

        // oldPath 相对于 oldPrefix 的部分：xxxx\yyy.txt
        Path suffix = oldPrefix.relativize(path);

        // 拼接新路径
        path = newPrefix.resolve(suffix);

        return !Files.exists(path);
    }

    @Override
    public List<FileRessource> handlePropFind(Path path, String uri) {

/*        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<d:multistatus xmlns:cal=\"urn:ietf:params:xml:ns:caldav\" xmlns:cs=\"http://calendarserver.org/ns/\" xmlns:card=\"urn:ietf:params:xml:ns:carddav\" xmlns:d=\"DAV:\">");


        File file = path.toFile();
        File[] children = file.isDirectory() ? file.listFiles() : new File[0];
        File[] files = new File[children.length + 1];
        files[0] = file; // 当前资源本身放第一个
        for (int i = 0; i < children.length; i++) {
            files[i + 1] = children[i];
        }
        if (files != null) {
            for (File f : files) {
                if (f == null) {
                    continue;
                }
                String name = f.getName();

                String fullPath;
                if (f.equals(file)) {
                    fullPath = uri;
                } else {
                    fullPath = uri.endsWith("/") ? uri + name : uri + "/" + name;
                }
                if (f.isDirectory() && !fullPath.endsWith("/")) {
                    fullPath += "/";
                }

                xml.append("<d:response> ");
                xml.append("<d:href>").append(encodeHref(fullPath)).append("</d:href> ");
                xml.append("<d:propstat> <d:prop> ");
                xml.append("<d:resourcetype>").append(f.isDirectory() ? "<d:collection/>" : "").append("</d:resourcetype> ");
                xml.append("<d:getcontentlength>").append(f.length()).append("</d:getcontentlength> ");
                xml.append("<d:getlastmodified>").append(
                        Files.getLastModifiedTime(f.toPath())
                                .toInstant()
                                .atZone(java.time.ZoneOffset.UTC)
                                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                ).append("</d:getlastmodified>");

                String safeDisplayName = f.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

                xml.append("<d:displayname>").append(safeDisplayName).append("</d:displayname>");
                xml.append("</d:prop> <d:status>HTTP/1.1 200 OK</d:status> </d:propstat> ");
                xml.append("</d:response> ");
            }
        }

        xml.append("</d:multistatus>");
        resp.setStatus(207);
        resp.getWriter().write(xml.toString());*/

        String adapter = "webdav";


        // 要替换的原始前缀路径
        Path oldPrefix = path;

        // 新的前缀路径
        Path newPrefix = Paths.get(adapter);

        // oldPath 相对于 oldPrefix 的部分：xxxx\yyy.txt
        Path suffix = oldPrefix.relativize(path);

        // 拼接新路径
        path = newPrefix.resolve(suffix);



        File file = path.toFile();
        File[] files = file.isDirectory() ? file.listFiles() : new File[0];

        List<FileRessource> list = new ArrayList<>();

        for (File f : files) {
            FileRessource ressource = new FileRessource();

            ressource.setType(f.isDirectory() ? "folder" : "file");
            ressource.setName(f.getName());
            try {
                ressource.setDate(new Date(Files.getLastModifiedTime(f.toPath()).toMillis()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            ressource.setSize(f.length());

            list.add(ressource);
        }

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
