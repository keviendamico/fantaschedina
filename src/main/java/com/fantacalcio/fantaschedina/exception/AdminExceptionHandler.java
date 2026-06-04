package com.fantacalcio.fantaschedina.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice(basePackages = "com.fantacalcio.fantaschedina.controller.admin")
public class AdminExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String handle(Exception e, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        log.warn("Admin operation failed [{}]: {}", request.getRequestURI(), e.getMessage());
        redirectAttributes.addFlashAttribute("error", e.getMessage());
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/admin/dashboard");
    }
}
