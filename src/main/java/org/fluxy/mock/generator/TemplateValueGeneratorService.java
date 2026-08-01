package org.fluxy.mock.generator;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TemplateValueGeneratorService {

    private final List<TemplateValueGenerator> generators;

    public TemplateValueGeneratorService(List<TemplateValueGenerator> generators) {
        this.generators = generators;
    }

    public Optional<String> generate(String type) {
        return generators.stream()
                .filter(generator -> generator.supports(type))
                .findFirst()
                .map(TemplateValueGenerator::generate);
    }
}
