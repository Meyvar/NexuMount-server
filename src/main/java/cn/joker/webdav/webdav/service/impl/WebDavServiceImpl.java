package cn.joker.webdav.webdav.service.impl;

import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.RequestStatus;
import cn.joker.webdav.webdav.service.IWebDavService;
import cn.joker.webdav.utils.RequestHolder;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

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
import java.util.ArrayList;
import java.util.List;

@Service
public class WebDavServiceImpl implements IWebDavService {

    @Override
    public void sendContent() throws IOException {
        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        String method = request.getMethod();
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);

        if (uri != null && uri.matches(".*/\\._.*")) {
            response.setStatus(404);
            return;
        }

        Path path = Paths.get(uri).normalize();

        System.out.println(method + "  uri: " + uri + "  path:" + path);

        response.setHeader("DAV", "1,2");
        response.setContentType("application/xml;charset=UTF-8");

        switch (method) {
            case "OPTIONS" -> handleOptions(response);
            case "PROPFIND" -> handlePropFind(response, path, uri);
            case "GET" -> handleGet(response, path, uri);
            case "PUT" -> handlePut(request, response, path, uri);
            case "DELETE" -> handleDelete(response, path, uri);
            case "MKCOL" -> handleMkcol(response, path, uri);
            case "LOCK" -> handleLock(request, response, path);
            case "UNLOCK" -> handleUnlock(request, response, path);
            case "MOVE" -> handleMove(request, response, path, uri);
            case "COPY" -> handleCopy(response, path, uri);
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

        AdapterManager adapterManager = new AdapterManager(path, uri);

        if (!adapterManager.hasPath()){
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<FileResource> list = new ArrayList<>();

        HttpServletRequest request = RequestHolder.getRequest();
        String depth = request.getHeader("depth");

        list.add(adapterManager.getFolderItself());

        if ("1".equals(depth)) {
            list.addAll(adapterManager.propFind());
        }


        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<d:multistatus xmlns:cal=\"urn:ietf:params:xml:ns:caldav\" xmlns:cs=\"http://calendarserver.org/ns/\" xmlns:card=\"urn:ietf:params:xml:ns:carddav\" xmlns:d=\"DAV:\">");


        for (FileResource resource : list) {
            if (resource == null) {
                continue;
            }

            String fullPath = resource.getHref();

            if (resource.getType().equals("folder") && !fullPath.endsWith("/")) {
                fullPath += "/";
            }

            xml.append("<d:response> ");
            xml.append("<d:href>").append(encodeHref(fullPath)).append("</d:href> ");
            xml.append("<d:propstat> <d:prop> ");
            xml.append("<d:resourcetype>").append("folder".equals(resource.getType()) ? "<d:collection/>" : "").append("</d:resourcetype> ");
            xml.append("<d:getcontentlength>").append(resource.getSize()).append("</d:getcontentlength> ");
            xml.append("<d:getlastmodified>").append(
                    resource.getDate()
                            .toInstant()
                            .atZone(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
            ).append("</d:getlastmodified>");

            String safeDisplayName = resource.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

            xml.append("<d:displayname>").append(safeDisplayName).append("</d:displayname>");
            xml.append("</d:prop> <d:status>HTTP/1.1 200 OK</d:status> </d:propstat> ");
            xml.append("</d:response> ");

        }
        xml.append("</d:multistatus>");


        byte[] data = xml.toString().getBytes(StandardCharsets.UTF_8);
        resp.setContentLength(data.length);
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

    private void handleGet(HttpServletResponse resp, Path path, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(path, uri);

        if (!adapterManager.hasPath()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        resp.setStatus(HttpServletResponse.SC_OK);
        adapterManager.get().transferTo(resp.getOutputStream());
    }

    private void handlePut(HttpServletRequest req, HttpServletResponse resp, Path path, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(path, uri);
        adapterManager.put();
        // 响应
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }


    private void handleDelete(HttpServletResponse resp, Path path, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(path, uri);

        if (!adapterManager.hasPath()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        adapterManager.delete();
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void handleMkcol(HttpServletResponse resp, Path path, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(path, uri);

        if (adapterManager.hasPath()) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        adapterManager.mkcol();
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

    private void handleMove(HttpServletRequest req, HttpServletResponse resp, Path sourcePath, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(sourcePath, uri);
        RequestStatus status = adapterManager.move();

        if (status.isSuccess()) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } else {
            resp.sendError(status.getCode(), status.getMessage());
        }
    }

    private void handleCopy(HttpServletResponse resp, Path sourcePath, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(sourcePath, uri);
        RequestStatus status = adapterManager.copy();
        if (status.isSuccess()) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } else {
            resp.sendError(status.getCode(), status.getMessage());
        }
    }
}
