package cn.joker.webdav.webdav.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.joker.webdav.business.entity.SysUser;
import cn.joker.webdav.business.service.ISysUserService;
import cn.joker.webdav.webdav.adapter.contract.AdapterManager;
import cn.joker.webdav.webdav.entity.FileResource;
import cn.joker.webdav.webdav.entity.GetFileResource;
import cn.joker.webdav.webdav.entity.RequestParam;
import cn.joker.webdav.webdav.entity.RequestStatus;
import cn.joker.webdav.webdav.service.IWebDavService;
import cn.joker.webdav.utils.RequestHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class WebDavServiceImpl implements IWebDavService {

    @Autowired
    private ISysUserService sysUserService;

    private String userPath;

    @Override
    public void sendContent() throws Exception {
        HttpServletRequest request = RequestHolder.getRequest();
        HttpServletResponse response = RequestHolder.getResponse();

        SysUser sysUser = sysUserService.getById(StpUtil.getLoginId().toString());

        userPath = sysUser.getRootPath();

        if ("/".equals(userPath)) {
            userPath = "";
        }


        String method = request.getMethod();
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);

        if (uri != null && uri.matches(".*/\\._.*")) {
            response.setStatus(404);
            return;
        }

        Path path = Paths.get(uri).normalize();

        System.out.println(method + "  uri: " + uri + "  path:" + path);

        response.setHeader("DAV", "1,2");

        switch (method) {
            case "OPTIONS" -> handleOptions(response);
            case "PROPFIND" -> handlePropFind(response, path, uri);
            case "GET" -> handleGet(response, path, uri);
            case "PUT" -> {
                if (!sysUser.getPermissions().contains("createOrUpload")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                handlePut(request, response, path, uri);
            }
            case "DELETE" -> {
                if (!sysUser.getPermissions().contains("remove")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                handleDelete(response, path, uri);
            }
            case "MKCOL" -> {
                if (!sysUser.getPermissions().contains("createOrUpload")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                handleMkcol(response, path, uri);
            }
            case "LOCK" -> handleLock(request, response, path);
            case "UNLOCK" -> handleUnlock(request, response, path);
            case "MOVE" -> {
                if (!sysUser.getPermissions().contains("move")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                handleMove(request, response, path, uri);
            }
            case "COPY" -> {
                if (!sysUser.getPermissions().contains("copy")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                handleCopy(response, path, uri);
            }
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

        AdapterManager adapterManager = new AdapterManager(uri, userPath);

        if (!adapterManager.hasPath()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<FileResource> list = new ArrayList<>();

        HttpServletRequest request = RequestHolder.getRequest();
        String depth = request.getHeader("depth");

        list.add(adapterManager.getFolderItself());

        if ("1".equals(depth)) {
            RequestStatus status = adapterManager.propFind(false);
            if (!status.isSuccess()) {
                resp.sendError(status.getCode(), status.getMessage());
                return;
            }
            list.addAll(status.getFileResources());
        }


        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        xml.append("<d:multistatus xmlns:cal=\"urn:ietf:params:xml:ns:caldav\" xmlns:cs=\"http://calendarserver.org/ns/\" xmlns:card=\"urn:ietf:params:xml:ns:carddav\" xmlns:d=\"DAV:\">");


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);


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
                    resource.getDate().toInstant().atZone(ZoneOffset.UTC).format(formatter)
            ).append("</d:getlastmodified>");

            String safeDisplayName = resource.getName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");

            xml.append("<d:displayname>").append(safeDisplayName).append("</d:displayname>");

            if (!"folder".equals(resource.getType())) {
                xml.append("<d:getcontenttype>").append(resource.getContentType()).append("</d:getcontenttype>");
            }

            xml.append("</d:prop> <d:status>HTTP/1.1 200 OK</d:status> </d:propstat> ");
            xml.append("</d:response> ");

        }
        xml.append("</d:multistatus>");
        resp.setContentType("application/xml;charset=UTF-8");


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
                encoded.append(URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }
        if (uri.endsWith("/")) {
            encoded.append("/");
        }
        return encoded.isEmpty() ? "/" : encoded.toString();
    }

    private void handleGet(HttpServletResponse resp, Path path, String uri) throws Exception {

        AdapterManager adapterManager = new AdapterManager(uri, userPath);

        if (!adapterManager.hasPath()) {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        adapterManager.get();
    }

    private void handlePut(HttpServletRequest req, HttpServletResponse resp, Path path, String uri) throws Exception {
        if (uri.contains(".DS_Store")) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        AdapterManager adapterManager = new AdapterManager(uri, userPath);
        adapterManager.put(req.getInputStream());
        // 响应
        resp.setStatus(HttpServletResponse.SC_CREATED);
    }


    private void handleDelete(HttpServletResponse resp, Path path, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(uri, userPath);

        RequestStatus status = adapterManager.delete();
        if (!status.isSuccess()) {
            resp.sendError(status.getCode(), status.getMessage());
            return;
        }
        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void handleMkcol(HttpServletResponse resp, Path path, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(uri, userPath);

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
        AdapterManager adapterManager = new AdapterManager(uri, userPath);

        HttpServletRequest request = RequestHolder.getRequest();
        String destHeader = request.getHeader("Destination");

        if (destHeader == null) {
            throw new RuntimeException("目标地址不能为空！");
        }

        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        AdapterManager toAdapterManager = new AdapterManager(destPathRaw, userPath);

        RequestStatus status = adapterManager.move(toAdapterManager);

        if (status.isSuccess()) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } else {
            resp.sendError(status.getCode(), status.getMessage());
        }
    }

    private void handleCopy(HttpServletResponse resp, Path sourcePath, String uri) throws IOException {
        AdapterManager adapterManager = new AdapterManager(uri, userPath);
        HttpServletRequest request = RequestHolder.getRequest();
        String destHeader = request.getHeader("Destination");

        if (destHeader == null) {
            throw new RuntimeException("目标地址不能为空！");
        }

        URI destUriObj = URI.create(destHeader);
        String destPathRaw = destUriObj.getPath();

        AdapterManager toAdapterManager = new AdapterManager(destPathRaw, userPath);
        RequestStatus status = adapterManager.copy(toAdapterManager);
        if (status.isSuccess()) {
            resp.setStatus(HttpServletResponse.SC_CREATED);
        } else {
            resp.sendError(status.getCode(), status.getMessage());
        }
    }
}
