package org.fluxy.mock.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TemplateEngineService}.
 *
 * Covers:
 *  - Built-in generators: {{uuid}}, {{timestamp}}, {{now}}
 *  - Path and query variable resolution
 *  - Shared context: same generated value across multiple resolve() calls
 *  - Named variable binding: {{generator:alias}} — generate-once / reuse
 *  - Fallback for unknown placeholders
 */
class TemplateEngineServiceTest {

    private TemplateEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new TemplateEngineService();
    }

    // ── Built-in generators ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Built-in generators")
    class BuiltInGenerators {

        @Test
        @DisplayName("{{uuid}} resolves to a valid UUID v4 string")
        void uuidIsValidUuid() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{uuid}}", ctx);
            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("{{timestamp}} resolves to a numeric epoch-millis string")
        void timestampIsNumeric() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{timestamp}}", ctx);
            assertThat(result).matches("\\d+");
            assertThat(Long.parseLong(result)).isPositive();
        }

        @Test
        @DisplayName("{{now}} resolves to an ISO-8601 string")
        void nowIsIso8601() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{now}}", ctx);
            // ISO-8601 instant: e.g. 2026-04-27T12:00:00.000Z
            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        }

        @Test
        @DisplayName("null template returns null")
        void nullTemplateReturnsNull() {
            Map<String, String> ctx = engine.buildContext(null, null);
            assertThat(engine.resolve(null, ctx)).isNull();
        }
    }

    // ── Path and query variables ──────────────────────────────────────────────

    @Nested
    @DisplayName("Path and query variables")
    class PathAndQueryVars {

        @Test
        @DisplayName("{{path.id}} resolves from path variables")
        void pathVariable() {
            Map<String, String> pathVars = Map.of("id", "42");
            Map<String, String> ctx = engine.buildContext(pathVars, null);
            assertThat(engine.resolve("{{path.id}}", ctx)).isEqualTo("42");
        }

        @Test
        @DisplayName("{{id}} resolves as shortcut for {{path.id}}")
        void pathVariableShortcut() {
            Map<String, String> pathVars = Map.of("id", "42");
            Map<String, String> ctx = engine.buildContext(pathVars, null);
            assertThat(engine.resolve("{{id}}", ctx)).isEqualTo("42");
        }

        @Test
        @DisplayName("{{query.page}} resolves from query params")
        void queryParam() {
            Map<String, String> queryParams = Map.of("page", "3");
            Map<String, String> ctx = engine.buildContext(null, queryParams);
            assertThat(engine.resolve("{{query.page}}", ctx)).isEqualTo("3");
        }

        @Test
        @DisplayName("{{page}} resolves as shortcut for query param")
        void queryParamShortcut() {
            Map<String, String> queryParams = Map.of("page", "3");
            Map<String, String> ctx = engine.buildContext(null, queryParams);
            assertThat(engine.resolve("{{page}}", ctx)).isEqualTo("3");
        }

        @Test
        @DisplayName("Path variable takes precedence over query param with same name")
        void pathTakesPrecedenceOverQuery() {
            Map<String, String> pathVars = Map.of("id", "from-path");
            Map<String, String> queryParams = Map.of("id", "from-query");
            Map<String, String> ctx = engine.buildContext(pathVars, queryParams);
            assertThat(engine.resolve("{{id}}", ctx)).isEqualTo("from-path");
        }

        @Test
        @DisplayName("Unknown placeholder is kept as-is")
        void unknownPlaceholderKeptAsIs() {
            Map<String, String> ctx = engine.buildContext(null, null);
            assertThat(engine.resolve("{{unknown}}", ctx)).isEqualTo("{{unknown}}");
        }
    }

    // ── Shared context ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Shared context across multiple resolve() calls")
    class SharedContext {

        @Test
        @DisplayName("{{uuid}} returns the same value in two consecutive calls sharing the same context")
        void uuidIsConsistentAcrossCalls() {
            Map<String, String> ctx = engine.buildContext(null, null);

            String bodyResolved = engine.resolve("{\"id\": \"{{uuid}}\"}", ctx);
            String sqsResolved  = engine.resolve("{\"id\": \"{{uuid}}\"}", ctx);

            String uuidInBody = bodyResolved.replaceAll(".*\"id\": \"([^\"]+)\".*", "$1");
            String uuidInSqs  = sqsResolved.replaceAll(".*\"id\": \"([^\"]+)\".*", "$1");

            assertThat(uuidInBody).isEqualTo(uuidInSqs);
        }

        @Test
        @DisplayName("{{timestamp}} returns the same value in two calls sharing the same context")
        void timestampIsConsistentAcrossCalls() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String first  = engine.resolve("{{timestamp}}", ctx);
            String second = engine.resolve("{{timestamp}}", ctx);
            assertThat(first).isEqualTo(second);
        }
    }

    // ── Named variable binding ────────────────────────────────────────────────

    @Nested
    @DisplayName("Named variable binding  {{generator:alias}}")
    class NamedBinding {

        @Test
        @DisplayName("{{uuid:orderId}} generates a UUID and stores it under 'orderId'")
        void namedUuidIsStoredInContext() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{uuid:orderId}}", ctx);
            assertThat(result).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
            assertThat(ctx).containsKey("orderId");
            assertThat(ctx.get("orderId")).isEqualTo(result);
        }

        @Test
        @DisplayName("{{uuid:orderId}} used twice in the same template returns the same UUID")
        void namedUuidIsIdempotentWithinSameTemplate() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{uuid:orderId}} {{uuid:orderId}}", ctx);
            String[] parts = result.split(" ");
            assertThat(parts).hasSize(2);
            assertThat(parts[0]).isEqualTo(parts[1]);
        }

        @Test
        @DisplayName("{{uuid:orderId}} in body and {{orderId}} in SQS template share the same value")
        void namedUuidSharedBetweenBodyAndSqsTemplate() {
            Map<String, String> ctx = engine.buildContext(null, null);

            String body = engine.resolve(
                    "{\"orderId\": \"{{uuid:orderId}}\", \"status\": \"created\"}", ctx);
            String sqs  = engine.resolve(
                    "{\"event\": \"ORDER_CREATED\", \"orderId\": \"{{orderId}}\"}", ctx);

            String orderIdInBody = body.replaceAll(".*\"orderId\": \"([^\"]+)\".*", "$1");
            String orderIdInSqs  = sqs.replaceAll(".*\"orderId\": \"([^\"]+)\".*", "$1");

            assertThat(orderIdInBody).isEqualTo(orderIdInSqs);
            assertThat(orderIdInBody).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("Two different aliases produce two different UUIDs")
        void twoDifferentAliasesProduceDifferentUuids() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{uuid:id1}} {{uuid:id2}}", ctx);
            String[] parts = result.split(" ");
            assertThat(parts[0]).isNotEqualTo(parts[1]);
        }

        @Test
        @DisplayName("Named alias differs from the default pre-seeded {{uuid}}")
        void namedAliasDiffersFromDefaultUuid() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String defaultUuid = ctx.get("uuid");
            String named = engine.resolve("{{uuid:myId}}", ctx);
            // Named binding creates a fresh UUID independent of the pre-seeded one
            assertThat(named).isNotEqualTo(defaultUuid);
        }

        @Test
        @DisplayName("{{timestamp:ts}} generates and caches a timestamp alias")
        void namedTimestamp() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{timestamp:ts}}", ctx);
            assertThat(result).matches("\\d+");
            assertThat(ctx.get("ts")).isEqualTo(result);
        }

        @Test
        @DisplayName("{{now:eventTime}} generates and caches an ISO-8601 alias")
        void namedNow() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{now:eventTime}}", ctx);
            assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
            assertThat(ctx.get("eventTime")).isEqualTo(result);
        }

        @Test
        @DisplayName("Unknown generator with alias falls back to raw placeholder")
        void unknownGeneratorWithAliasFallback() {
            Map<String, String> ctx = engine.buildContext(null, null);
            String result = engine.resolve("{{unknown:myVar}}", ctx);
            assertThat(result).isEqualTo("{{unknown:myVar}}");
        }
    }

    // ── Convenience overload ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Convenience overload resolve(template, pathVars, queryParams)")
    class ConvenienceOverload {

        @Test
        @DisplayName("Resolves path and query vars correctly")
        void resolvesPathAndQuery() {
            String result = engine.resolve(
                    "id={{path.id}} page={{query.page}}",
                    Map.of("id", "7"),
                    Map.of("page", "2"));
            assertThat(result).isEqualTo("id=7 page=2");
        }

        @Test
        @DisplayName("Each call to the convenience overload produces an independent context")
        void eachCallHasIndependentContext() {
            String uuid1 = engine.resolve("{{uuid}}", null, null);
            String uuid2 = engine.resolve("{{uuid}}", null, null);
            // Different calls → different pre-seeded UUIDs (independent contexts)
            assertThat(uuid1).isNotEqualTo(uuid2);
        }
    }

    // ── Complex template ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Complex template scenarios")
    class ComplexTemplates {

        @Test
        @DisplayName("Full order creation flow: body + SQS message share orderId")
        void fullOrderCreationFlow() {
            Map<String, String> ctx = engine.buildContext(null, null);

            String bodyTemplate = "{\"orderId\": \"{{uuid:orderId}}\", \"status\": \"created\", \"at\": \"{{now}}\"}";
            String sqsTemplate  = "{\"event\": \"ORDER_CREATED\", \"orderId\": \"{{orderId}}\", \"at\": \"{{now}}\"}";

            String body = engine.resolve(bodyTemplate, ctx);
            String sqs  = engine.resolve(sqsTemplate, ctx);

            String orderIdInBody = extractJsonField(body, "orderId");
            String orderIdInSqs  = extractJsonField(sqs, "orderId");
            String atInBody      = extractJsonField(body, "at");
            String atInSqs       = extractJsonField(sqs, "at");

            assertThat(orderIdInBody).isEqualTo(orderIdInSqs)
                    .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            // {{now}} was pre-seeded once → same across both templates
            assertThat(atInBody).isEqualTo(atInSqs);
        }

        /** Extracts a JSON string field value from a simple inline JSON string. */
        private String extractJsonField(String json, String field) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(json);
            assertThat(m.find()).as("Field '%s' not found in: %s", field, json).isTrue();
            return m.group(1);
        }
    }
}

