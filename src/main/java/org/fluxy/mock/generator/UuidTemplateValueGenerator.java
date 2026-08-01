package org.fluxy.mock.generator;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("uuidTemplateValueGenerator")
public class UuidTemplateValueGenerator implements TemplateValueGenerator {

    @Override
    public String type() {
        return "uuid";
    }

    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
