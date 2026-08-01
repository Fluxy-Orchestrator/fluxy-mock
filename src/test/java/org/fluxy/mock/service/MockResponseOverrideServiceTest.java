package org.fluxy.mock.service;

import org.fluxy.mock.generator.NowTemplateValueGenerator;
import org.fluxy.mock.generator.TemplateValueGeneratorService;
import org.fluxy.mock.generator.TimestampTemplateValueGenerator;
import org.fluxy.mock.generator.UuidTemplateValueGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MockResponseOverrideServiceTest {

    private MockResponseOverrideService overrideService;

    @BeforeEach
    void setUp() {
        overrideService = new MockResponseOverrideService(
                new com.fasterxml.jackson.databind.ObjectMapper(),
                new TemplateEngineService(new TemplateValueGeneratorService(java.util.List.of(
                        new UuidTemplateValueGenerator(),
                        new TimestampTemplateValueGenerator(),
                        new NowTemplateValueGenerator()
                )))
        );
    }

    @Test
    void overridesNestedJsonFieldUsingTemplates() {
        ResponseEntity<String> response = ResponseEntity.ok("{\"user\":{\"name\":\"Ana\",\"status\":\"old\"},\"items\":[{\"qty\":1}]}");

        ResponseEntity<String> transformed = overrideService.apply(
                response,
                Map.of(
                        "user.status", "\"active\"",
                        "user.name", "\"{{path.id}}\"",
                        "items[0].qty", "5"
                ),
                Map.of("id", "42"),
                Map.of());

        assertThat(transformed.getBody()).contains("\"status\":\"active\"");
        assertThat(transformed.getBody()).contains("\"name\":\"42\"");
        assertThat(transformed.getBody()).contains("\"qty\":5");
    }
}
