package cn.joker.webdav.business.controller;

import cn.joker.webdav.business.service.IWebDavService;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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

@RestController
public class WebDavController {


    @Autowired
    private IWebDavService webDavService;

    @RequestMapping(value = "/**", method = RequestMethod.OPTIONS)
    public void options() throws IOException {
        webDavService.sendContent();
    }

    @RequestMapping("/**")
    public void handle() throws IOException {
        webDavService.sendContent();
    }
}