package org.fluxy.mock.service;

import org.fluxy.mock.generator.TemplateValueGeneratorService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple template engine that replaces {{variable}} placeholders in a string.
 * Supports path variables ({{path.xxx}}), query params ({{query.xxx}}),
 * and generator-backed placeholders such as {{uuid}}, {{timestamp}} and {{now}}.
 */
@Service
public class TemplateEngineService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([\\w.]+)(?::([\\w.]+))?\\s*}}");

    private final TemplateValueGeneratorService generatorService;

    public TemplateEngineService(TemplateValueGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

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

        if (pathVars != null) {
            pathVars.forEach((k, v) -> {
                ctx.put("path." + k, v);
                ctx.putIfAbsent(k, v);
            });
        }

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
     *
     * Syntax:
     * <ul>
     *   <li>{{key}} — looks up {@code key} in the context (e.g. {{path.id}})</li>
     *   <li>{{generator:alias}} — generates a value using the matching generator,
     *       stores it in the context under {@code alias}, and returns it.</li>
     * </ul>
     *
     * @param template the raw template string (may be null)
     * @param ctx      the shared mutable context produced by {@link #buildContext}
     * @return resolved string, or null if template was null
     */
    public String resolve(String template, Map<String, String> ctx) {
        if (template == null) {
            return null;
        }

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String type = matcher.group(1);
            String alias = matcher.group(2);

            String replacement = resolvePlaceholder(matcher.group(0), type, alias, ctx);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Convenience overload: builds a fresh context and resolves the template.
     * Use this only when you do NOT need to share the context with other templates.
     */
    public String resolve(String template, Map<String, String> pathVars, Map<String, String> queryParams) {
        return resolve(template, buildContext(pathVars, queryParams));
    }

    private String resolvePlaceholder(String rawPlaceholder, String type, String alias, Map<String, String> ctx) {
        String contextKey = alias != null ? alias : type;
        if (ctx.containsKey(contextKey)) {
            return ctx.get(contextKey);
        }

        String generated = generatorService.generate(type).orElse(rawPlaceholder);
        ctx.put(contextKey, generated);
        return generated;
    }
}
