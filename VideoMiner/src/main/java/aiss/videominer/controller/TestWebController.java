package aiss.videominer.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TestWebController {
    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }
}