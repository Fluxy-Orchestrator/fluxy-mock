package org.fluxy.mock.generator;

/**
 * Visitor-style generator for a placeholder type.
 */
public interface TemplateValueGenerator {

    String type();

    String generate();

    default boolean supports(String placeholderType) {
        return type().equalsIgnoreCase(placeholderType);
    }
}
