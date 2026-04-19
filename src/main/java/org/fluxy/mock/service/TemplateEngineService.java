package org.fluxy.mock.service;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple template engine that replaces {{variable}} placeholders in a string.
 * Supports path variables ({{path.xxx}}), query params ({{query.xxx}}),
 * and built-in generators: {{uuid}}, {{timestamp}}, {{now}}.
 */
@Service
public class TemplateEngineService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*}}");

    /**
     * Resolve all {{…}} placeholders in the template.
     *
     * @param template   the raw template string (may be null)
     * @param pathVars   variables extracted from the URL path
     * @param queryParams query parameters from the request
     * @return resolved string, or null if template was null
     */
    public String resolve(String template, Map<String, String> pathVars, Map<String, String> queryParams) {
        if (template == null) return null;

        Map<String, String> context = buildContext(pathVars, queryParams);

        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = context.getOrDefault(key, m.group(0)); // leave unresolved as-is
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private Map<String, String> buildContext(Map<String, String> pathVars, Map<String, String> queryParams) {
        Map<String, String> ctx = new HashMap<>();

        // Built-in generators
        ctx.put("uuid", UUID.randomUUID().toString());
        ctx.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        ctx.put("now", Instant.now().toString());

        // Path variables: accessible as {{path.id}} or just {{id}}
        if (pathVars != null) {
            pathVars.forEach((k, v) -> {
                ctx.put("path." + k, v);
                ctx.putIfAbsent(k, v);
            });
        }

        // Query params: accessible as {{query.page}} or just {{page}} (path vars take precedence)
        if (queryParams != null) {
            queryParams.forEach((k, v) -> {
                ctx.put("query." + k, v);
                ctx.putIfAbsent(k, v);
            });
        }

        return ctx;
    }
}

