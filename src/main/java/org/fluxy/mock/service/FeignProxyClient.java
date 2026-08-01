package org.fluxy.mock.service;

import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds a dynamic Feign request to the real service and maps the response back.
 */
@Slf4j
@Service
public class FeignProxyClient {

    public record ProxyResult(ResponseEntity<String> responseEntity, boolean cacheable) {}

    /**
     * Forward the incoming request to the real service.
     *
     * @param targetBaseUrl e.g. https://api.example.com
     * @param method        HTTP method
     * @param path          sub-path after base url, e.g. /users/123
     * @param queryParams   original query parameters
     * @param headers       original headers (will be forwarded)
     * @param body          original request body (may be null)
     * @return ResponseEntity with the real service's response
     */
    public ResponseEntity<String> forward(String targetBaseUrl, String method, String path,
                                           Map<String, String> queryParams,
                                           Map<String, Collection<String>> headers,
                                           byte[] body) {
        return forwardDetailed(targetBaseUrl, method, path, queryParams, headers, body).responseEntity();
    }

    public ProxyResult forwardDetailed(String targetBaseUrl, String method, String path,
                                       Map<String, String> queryParams,
                                       Map<String, Collection<String>> headers,
                                       byte[] body) {
        String url = targetBaseUrl.replaceAll("/+$", "") + path;

        // Build query string
        if (queryParams != null && !queryParams.isEmpty()) {
            StringJoiner qj = new StringJoiner("&", "?", "");
            queryParams.forEach((k, v) -> qj.add(k + "=" + v));
            url += qj.toString();
        }

        log.info("Proxying {} {} to real service", method, url);

        RequestTemplate template = new RequestTemplate();
        template.method(Request.HttpMethod.valueOf(method.toUpperCase()));
        template.target(url);
        if (headers != null) {
            headers.forEach(template::header);
        }
        if (body != null && body.length > 0) {
            template.body(body, StandardCharsets.UTF_8);
        }

        Request request = template.resolve(new HashMap<>()).request();

        try (Response response = new feign.Client.Default(null, null).execute(request, new Request.Options())) {
            int status = response.status();
            HttpHeaders responseHeaders = new HttpHeaders();
            if (response.headers() != null) {
                response.headers().forEach((k, v) -> responseHeaders.addAll(k, new ArrayList<>(v)));
            }
            String responseBody = null;
            if (response.body() != null) {
                responseBody = new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
            ResponseEntity<String> responseEntity = ResponseEntity.status(HttpStatusCode.valueOf(status))
                    .headers(responseHeaders)
                    .body(responseBody);
            return new ProxyResult(responseEntity, true);
        } catch (IOException e) {
            log.error("Error proxying request to {}: {}", url, e.getMessage(), e);
            ResponseEntity<String> responseEntity = ResponseEntity.status(502)
                    .body("{\"error\":\"Bad Gateway\",\"message\":\"" + e.getMessage() + "\"}");
            return new ProxyResult(responseEntity, false);
        }
    }
}
