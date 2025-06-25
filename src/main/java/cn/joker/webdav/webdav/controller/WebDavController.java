package cn.joker.webdav.webdav.controller;

import cn.joker.webdav.webdav.service.IWebDavService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

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