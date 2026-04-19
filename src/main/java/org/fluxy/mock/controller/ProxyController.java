package org.fluxy.mock.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.fluxy.mock.service.MockResolverService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catch-all proxy controller.
 * Every request to /fluxy/mock/** is intercepted here and either
 * resolved to a mock response or proxied to the real service.
 */
@RestController
@RequiredArgsConstructor
public class ProxyController {

    private final MockResolverService resolverService;

    @RequestMapping("/fluxy/mock/**")
    public ResponseEntity<String> proxy(HttpServletRequest request) {
        String fullPath = request.getRequestURI();
        // Extract the sub-path after /fluxy/mock
        String subPath = fullPath.substring("/fluxy/mock".length());
        if (subPath.isEmpty()) subPath = "/";
        return resolverService.resolve(subPath, request);
    }
}

