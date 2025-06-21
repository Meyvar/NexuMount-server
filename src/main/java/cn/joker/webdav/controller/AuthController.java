package cn.joker.webdav.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @GetMapping
    public void indexPage(HttpServletResponse response) {

    }

    @PostMapping("/login")
    public void login(HttpServletRequest request, HttpServletResponse response) throws Exception {

    }
}
