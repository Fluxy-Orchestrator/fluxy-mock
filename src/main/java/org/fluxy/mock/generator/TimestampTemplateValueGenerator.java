package org.fluxy.mock.generator;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component("timestampTemplateValueGenerator")
public class TimestampTemplateValueGenerator implements TemplateValueGenerator {

    @Override
    public String type() {
        return "timestamp";
    }

    @Override
    public String generate() {
        return String.valueOf(Instant.now().toEpochMilli());
    }
}
