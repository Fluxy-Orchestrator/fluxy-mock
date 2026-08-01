package org.fluxy.mock.generator;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component("nowTemplateValueGenerator")
public class NowTemplateValueGenerator implements TemplateValueGenerator {

    @Override
    public String type() {
        return "now";
    }

    @Override
    public String generate() {
        return Instant.now().toString();
    }
}
