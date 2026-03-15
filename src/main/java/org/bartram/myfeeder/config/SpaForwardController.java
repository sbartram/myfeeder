package org.bartram.myfeeder.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/feed/**", "/folder/**", "/starred", "/boards", "/board/**", "/settings"})
    public String forward() {
        return "forward:/index.html";
    }
}
