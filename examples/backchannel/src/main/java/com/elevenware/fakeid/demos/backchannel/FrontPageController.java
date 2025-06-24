package com.elevenware.fakeid.demos.backchannel;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class FrontPageController {

    @GetMapping("/")
    public ModelAndView frontPage(@AuthenticationPrincipal AuthenticationPrincipal principal) {
        ModelAndView modelAndView = new ModelAndView("index");
        modelAndView.addObject("title", "Fake ID Backchannel Demo");
        modelAndView.addObject("message", "Welcome to the Fake ID Backchannel Demo!");
        return modelAndView;
    }

}
