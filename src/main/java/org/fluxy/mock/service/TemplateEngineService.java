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
 * built-in generators: {{uuid}}, {{timestamp}}, {{now}},
 * and named variable binding: {{uuid:orderId}} — generates once, reusable as {{orderId}}.
 */
@Service
public class TemplateEngineService {

    // Matches {{key}} or {{generator:alias}}
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)(?::([\\w.]+))?\\s*}}");

    /**
     * Build a mutable context for a request.
     * This context can be shared across all templates within the same request
     * (body template + post-action templates) so generated values are consistent.
     *
     * @param pathVars    variables extracted from the URL path
     * @param queryParams query parameters from the request
     * @return a mutable map to be passed to {@link #resolve(String, Map)}
     */
    public Map<String, String> buildContext(Map<String, String> pathVars, Map<String, String> queryParams) {
        Map<String, String> ctx = new HashMap<>();

        // Built-in generators pre-seeded with default names
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

    /**
     * Resolve all {{…}} placeholders using a shared, mutable context.
     * <p>
     * Syntax:
     * <ul>
     *   <li>{{key}} — looks up {@code key} in the context (e.g. {{uuid}}, {{path.id}})</li>
     *   <li>{{generator:alias}} — generates a value via the built-in generator, stores it
     *       in the context under {@code alias} for reuse, and returns it.
     *       If {@code alias} is already set, the cached value is returned.
     *       Example: {@code {{uuid:orderId}}} → same UUID each time within this context.</li>
     * </ul>
     *
     * @param template the raw template string (may be null)
     * @param ctx      the shared mutable context produced by {@link #buildContext}
     * @return resolved string, or null if template was null
     */
    public String resolve(String template, Map<String, String> ctx) {
        if (template == null) return null;

        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String alias = m.group(2); // null when no :alias suffix

            String replacement;
            if (alias != null) {
                // Named binding: {{uuid:orderId}} — generate once and cache under alias
                final String fallback = m.group(0);
                replacement = ctx.computeIfAbsent(alias, k -> {
                    String generated = generateBuiltin(key);
                    return generated != null ? generated : fallback;
                });
            } else {
                // Simple lookup: {{uuid}}, {{orderId}}, {{path.id}}, etc.
                replacement = ctx.getOrDefault(key, m.group(0));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Convenience overload: builds a fresh context and resolves the template.
     * Use this only when you do NOT need to share the context with other templates.
     */
    public String resolve(String template, Map<String, String> pathVars, Map<String, String> queryParams) {
        return resolve(template, buildContext(pathVars, queryParams));
    }

    // ── Built-in value generators ─────────────────────────────────────────────

    private String generateBuiltin(String name) {
        return switch (name) {
            case "uuid" -> UUID.randomUUID().toString();
            case "timestamp" -> String.valueOf(Instant.now().toEpochMilli());
            case "now" -> Instant.now().toString();
            default -> null;
        };
    }
}
