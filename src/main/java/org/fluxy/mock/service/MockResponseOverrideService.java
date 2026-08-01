package org.fluxy.mock.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MockResponseOverrideService {

    private final ObjectMapper objectMapper;
    private final TemplateEngineService templateEngine;

    public ResponseEntity<String> apply(ResponseEntity<String> responseEntity,
                                        Map<String, String> overrides,
                                        Map<String, String> pathVars,
                                        Map<String, String> queryParams) {
        if (responseEntity == null || responseEntity.getBody() == null
                || overrides == null || overrides.isEmpty()) {
            return responseEntity;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(responseEntity.getBody());
        } catch (Exception e) {
            return responseEntity;
        }
        if (!root.isObject()) {
            return responseEntity;
        }

        Map<String, String> ctx = templateEngine.buildContext(pathVars, queryParams);
        ObjectNode objectRoot = (ObjectNode) root;
        overrides.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> applyOverride(objectRoot, entry.getKey(),
                        templateEngine.resolve(entry.getValue(), ctx)));

        HttpHeaders headers = new HttpHeaders();
        responseEntity.getHeaders().forEach((name, values) -> headers.put(name, new ArrayList<>(values)));
        return ResponseEntity.status(responseEntity.getStatusCode()).headers(headers).body(objectRoot.toString());
    }

    private void applyOverride(ObjectNode root, String path, String rawValue) {
        if (path == null || path.isBlank()) {
            return;
        }
        List<PathToken> tokens = parsePath(path);
        if (tokens.isEmpty()) {
            return;
        }

        JsonNode valueNode = parseValueNode(rawValue);
        JsonNode current = root;
        for (int i = 0; i < tokens.size(); i++) {
            PathToken token = tokens.get(i);
            boolean last = i == tokens.size() - 1;

            if (!(current instanceof ObjectNode) && !(current instanceof ArrayNode)) {
                return;
            }

            if (current instanceof ObjectNode objectNode) {
                if (last) {
                    if (token.index() == null) {
                        objectNode.set(token.name(), valueNode);
                    } else {
                        ArrayNode arrayNode = ensureArray(objectNode, token.name());
                        setArrayValue(arrayNode, token.index(), valueNode);
                    }
                    return;
                }

                if (token.index() == null) {
                    current = ensureObject(objectNode, token.name(), tokens.get(i + 1));
                } else {
                    ArrayNode arrayNode = ensureArray(objectNode, token.name());
                    current = ensureArrayElement(arrayNode, token.index(), tokens.get(i + 1));
                }
                continue;
            }

            ArrayNode arrayNode = (ArrayNode) current;
            current = ensureArrayElement(arrayNode, token.index() != null ? token.index() : 0, tokens.get(i + 1));
        }
    }

    private ObjectNode ensureObject(ObjectNode parent, String field, PathToken nextToken) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        if (existing instanceof ArrayNode arrayNode && nextToken.index() != null) {
            return ensureArrayElement(arrayNode, nextToken.index(), nextToken);
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private ArrayNode ensureArray(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing instanceof ArrayNode arrayNode) {
            return arrayNode;
        }
        ArrayNode created = objectMapper.createArrayNode();
        parent.set(field, created);
        return created;
    }

    private ObjectNode ensureArrayElement(ArrayNode arrayNode, int index, PathToken nextToken) {
        while (arrayNode.size() <= index) {
            arrayNode.add(objectMapper.createObjectNode());
        }
        JsonNode existing = arrayNode.get(index);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        arrayNode.set(index, created);
        return created;
    }

    private void setArrayValue(ArrayNode arrayNode, int index, JsonNode valueNode) {
        while (arrayNode.size() <= index) {
            arrayNode.addNull();
        }
        arrayNode.set(index, valueNode);
    }

    private JsonNode parseValueNode(String rawValue) {
        if (rawValue == null) {
            return objectMapper.nullNode();
        }
        try {
            return objectMapper.readTree(rawValue);
        } catch (Exception e) {
            return objectMapper.getNodeFactory().textNode(rawValue);
        }
    }

    private List<PathToken> parsePath(String path) {
        List<PathToken> tokens = new ArrayList<>();
        for (String segment : path.split("\\.")) {
            if (segment.isBlank()) {
                continue;
            }
            int bracket = segment.indexOf('[');
            if (bracket < 0) {
                tokens.add(new PathToken(segment, null));
                continue;
            }
            String name = segment.substring(0, bracket);
            int end = segment.indexOf(']', bracket);
            if (name.isBlank() || end < 0) {
                continue;
            }
            Integer index = Integer.parseInt(segment.substring(bracket + 1, end));
            tokens.add(new PathToken(name, index));
        }
        return tokens;
    }

    private record PathToken(String name, Integer index) {}
}
