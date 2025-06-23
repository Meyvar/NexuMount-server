/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.milton.servlet;

import io.milton.http.*;
import io.milton.http.Response.ContentType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ServletRequest extends AbstractRequest {

    private static final Logger log = LoggerFactory.getLogger(ServletRequest.class);

    public static BeanCookie toBeanCookie(jakarta.servlet.http.Cookie c) {
        BeanCookie bc = new BeanCookie(c.getName());
        bc.setDomain(c.getDomain());
        bc.setExpiry(c.getMaxAge());
        bc.setHttpOnly(true); // http only by default
        bc.setPath(c.getPath());
        bc.setSecure(c.getSecure());
        bc.setValue(c.getValue());
        bc.setVersion(c.getVersion());
        return bc;
    }

    private final HttpServletRequest request;
    private final ServletContext  servletContext;
    private final Method method;
    private final String url;
    private Auth auth;
    private static final Map<ContentType, String> contentTypes = new EnumMap<>(ContentType.class);
    private static final Map<String, ContentType> typeContents = new HashMap<>();

    static {
        contentTypes.put(ContentType.HTTP, Response.HTTP);
        contentTypes.put(ContentType.MULTIPART, Response.MULTIPART);
        contentTypes.put(ContentType.XML, Response.XML);
        for (ContentType key : contentTypes.keySet()) {
            typeContents.put(contentTypes.get(key), key);
        }
    }

    private static final ThreadLocal<HttpServletRequest> tlRequest = new ThreadLocal<>();
    private static final ThreadLocal<ServletContext> tlServletContext = new ThreadLocal<>();

    public static HttpServletRequest getRequest() {
        return tlRequest.get();
    }

    static void clearThreadLocals() {
        tlRequest.remove();
        tlServletContext.remove();
    }

    public ServletRequest(HttpServletRequest r, ServletContext servletContext) {
        this.request = r;
        this.servletContext = servletContext;
        String sMethod = r.getMethod();
        method = Method.valueOf(sMethod);
        url = r.getRequestURL().toString();
        tlRequest.set(r);
        tlServletContext.set(servletContext);

        if (log.isTraceEnabled()) {
            log.trace("Dumping headers ---- " + r.getMethod() + " " + r.getRequestURL() + " -----");
            log.trace("Request class: " + r.getClass());
            log.trace("Response class: " + r.getClass());
            Enumeration names = r.getHeaderNames();
            while (names.hasMoreElements()) {
                String name = (String) names.nextElement();
                String value = r.getHeader(name);
                log.trace("  " + name + "=" + value);
            }
            log.trace("-------------------------------------------");
        }
    }

    @Override
    public String getFromAddress() {
        return request.getRemoteHost();
    }

    @Override
    public String getRequestHeader(Header header) {
        return request.getHeader(header.code);
    }

    @Override
    public Method getMethod() {
        return method;
    }

    @Override
    public String getAbsoluteUrl() {
        return url;
    }

    @Override
    public Auth getAuthorization() {
        if (auth != null) {
            return auth;
        }
        String enc = getRequestHeader(Header.AUTHORIZATION);
        if (enc == null) {
            log.trace("getAuthorization: No http credentials in request headers");
            return null;
        }
        if (enc.isEmpty()) {
            log.trace("getAuthorization: No http credentials in request headers; authorization header is not-null, but is empty");
            return null;
        }
        auth = new Auth(enc);
        if (log.isTraceEnabled()) {
            log.trace("creating new auth object {}", auth.getScheme());
        }
        return auth;
    }

    @Override
    public void setAuthorization(Auth auth) {
        this.auth = auth;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return request.getInputStream();
    }

    @Override
    public void parseRequestParameters(Map<String, String> params, Map<String, FileItem> files) throws RequestParseException {

    }


    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> map = new HashMap<>();
        Enumeration num = request.getHeaderNames();
        while (num.hasMoreElements()) {
            String name = (String) num.nextElement();
            String val = request.getHeader(name);
            map.put(name, val);
        }
        return map;
    }

    @Override
    public Cookie getCookie(String name) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if (c.getName().equals(name)) {
                    return toBeanCookie(c);
                }
            }
        }
        return null;
    }

    @Override
    public List<Cookie> getCookies() {
        ArrayList<Cookie> list = new ArrayList<>();
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                list.add(toBeanCookie(c));

            }
        }
        return list;
    }

    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    @Override
    public Locale getLocale() {
        return request.getLocale();
    }


}
