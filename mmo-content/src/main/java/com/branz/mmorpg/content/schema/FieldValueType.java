package com.branz.mmorpg.content.schema;

import com.fasterxml.jackson.databind.JsonNode;

public enum FieldValueType {
    STRING {
        @Override
        public boolean matches(JsonNode node) {
            return node.isTextual();
        }
    },
    INTEGER {
        @Override
        public boolean matches(JsonNode node) {
            return node.isIntegralNumber();
        }
    },
    NUMBER {
        @Override
        public boolean matches(JsonNode node) {
            return node.isNumber();
        }
    },
    BOOLEAN {
        @Override
        public boolean matches(JsonNode node) {
            return node.isBoolean();
        }
    },
    ARRAY {
        @Override
        public boolean matches(JsonNode node) {
            return node.isArray();
        }
    },
    OBJECT {
        @Override
        public boolean matches(JsonNode node) {
            return node.isObject();
        }
    };

    public abstract boolean matches(JsonNode node);

    public String jsonSchemaType() {
        return name().toLowerCase();
    }
}
