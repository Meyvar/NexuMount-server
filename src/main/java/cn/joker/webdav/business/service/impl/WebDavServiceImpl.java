package cn.joker.webdav.business.service.impl;

import cn.joker.webdav.business.service.IWebDavService;
import cn.joker.webdav.handle.FileHandle;
import cn.joker.webdav.utils.RequestHolder;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class WebDavServiceImpl implements IWebDavService {

    private FileHandle fileHandle;

    private static final String ROOT_DIR = "webdav";


    @Override
    public void sendContent() throws IOException {
        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        String method = request.getMethod();
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);

        Path path = Paths.get(ROOT_DIR, uri).normalize();

        response.setHeader("DAV", "1,2");
        response.setContentType("application/xml;charset=UTF-8");

        switch (method) {
            case "OPTIONS" -> handleOptions(response);
            case "PROPFIND" -> handlePropFind(response, path, uri);
            case "GET" -> handleGet(response, path);
            case "PUT" -> handlePut(request, response, path);
            case "DELETE" -> handleDelete(response, path);
            case "MKCOL" -> handleMkcol(response, path);
            case "LOCK" -> handleLock(request, response, path);
            case "UNLOCK" -> handleUnlock(request, response, path);
            case "MOVE" -> handleMove(request, response, path);
            default -> response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }


    private void handleOptions(HttpServletResponse resp) {
        resp.setHeader("Allow", "OPTIONS, GET, PUT, DELETE, PROPFIND, MKCOL");
        resp.setHeader("DAV", "1,2");
        resp.setHeader("MS-Author-Via", "DAV");
        resp.setStatus(HttpServletResponse.SC_OK);
    }

    private void handlePropFind(HttpServletResponse resp, Path path, String uri) throws IOException {
        if (!Files.exists(path)) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        StringBuilder xml = new StringBuilder();
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
        resp.getWriter().write(xml.toString());
    }

    private String encodeHref(String uri) {
        String[] segments = uri.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                encoded.append("/");
                try {
                    encoded.append(URLEncoder.encode(segment, "UTF-8").replace("+", "%20"));
                } catch (UnsupportedEncodingException e) {
                    encoded.append(segment);
                }
            }
        }
        if (uri.endsWith("/")) encoded.append("/");
        return encoded.length() == 0 ? "/" : encoded.toString();
    }

    private void handleGet(HttpServletResponse resp, Path path) throws IOException {
        if (!Files.exists(path)) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        resp.setStatus(HttpServletResponse.SC_OK);
        Files.copy(path, resp.getOutputStream());
    }

    private void handlePut(HttpServletRequest req, HttpServletResponse resp, Path path) throws IOException {
        long contentLength = req.getContentLengthLong();
        System.out.println("PUT: " + path + ", content-length: " + contentLength);

        Files.createDirectories(path.getParent());

        try (ServletInputStream input = req.getInputStream();
             OutputStream output = Files.newOutputStream(path)) {

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

        // 响应
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }


    private void handleDelete(HttpServletResponse resp, Path path) throws IOException {
        if (!Files.exists(path)) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Files.delete(path);
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void handleMkcol(HttpServletResponse resp, Path path) throws IOException {
        if (Files.exists(path)) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        Files.createDirectories(path);
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }

    private void handleLock(HttpServletRequest req, HttpServletResponse resp, Path path) throws IOException {
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType("application/xml; charset=UTF-8");

        String lockToken = "opaquelock:" + System.currentTimeMillis();
        resp.setHeader("Lock-Token", "<" + lockToken + ">");

        String xml = """
                <?xml version="1.0" encoding="utf-8"?>
                <D:prop xmlns:D="DAV:">
                    <D:lockdiscovery>
                        <D:activelock>
                            <D:locktype><D:write/></D:locktype>
                            <D:lockscope><D:exclusive/></D:lockscope>
                            <D:depth>Infinity</D:depth>
                            <D:owner>
                                <D:href>localhost</D:href>
                            </D:owner>
                            <D:timeout>Second-3600</D:timeout>
                            <D:locktoken>
                                <D:href>""" + lockToken + "</D:href> </D:locktoken> </D:activelock> </D:lockdiscovery></D:prop>";
        resp.getWriter().write(xml);
    }

    private void handleUnlock(HttpServletRequest req, HttpServletResponse resp, Path path) {
        // 直接返回成功即可（你也可以记录 token 验证）
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void handleMove(HttpServletRequest req, HttpServletResponse resp, Path sourcePath) throws IOException {
        String destHeader = req.getHeader("Destination");

        if (destHeader == null || !destHeader.startsWith("http")) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        Path destPath = Paths.get(ROOT_DIR, URLDecoder.decode(destPathRaw, StandardCharsets.UTF_8)).normalize();

        System.out.println("MOVE: " + sourcePath + " → " + destPath);

        if (!Files.exists(sourcePath)) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Files.createDirectories(destPath.getParent());

        boolean overwrite = !"F".equalsIgnoreCase(req.getHeader("Overwrite"));
        if (Files.exists(destPath)) {
            if (overwrite) {
                Files.delete(destPath);
            } else {
                resp.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
                return;
            }
        }

        Files.move(sourcePath, destPath);
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }
}
