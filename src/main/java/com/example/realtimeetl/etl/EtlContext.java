package com.example.realtimeetl.etl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EtlContext {

	private final String routeName;
	private final Map<String, Object> attributes = new LinkedHashMap<>();

	public EtlContext(String routeName) {
		this.routeName = requireText(routeName, "routeName must not be blank");
	}

	public String routeName() {
		return routeName;
	}

	public void put(String key, Object value) {
		requireText(key, "context key must not be blank");
		Objects.requireNonNull(value, "context value must not be null");
		attributes.put(key, value);
	}

	public Optional<Object> get(String key) {
		requireText(key, "context key must not be blank");
		return Optional.ofNullable(attributes.get(key));
	}

	public <T> T require(String key, Class<T> type) {
		requireText(key, "context key must not be blank");
		Objects.requireNonNull(type, "context type must not be null");
		Object value = attributes.get(key);
		if (value == null) {
			throw new IllegalStateException(
				"Route '%s' is missing required context key '%s'".formatted(routeName, key));
		}
		if (!type.isInstance(value)) {
			throw new IllegalStateException(
				"Route '%s' context key '%s' expected %s but found %s"
					.formatted(routeName, key, type.getName(), actualType(value)));
		}
		return type.cast(value);
	}

	public Map<String, Object> attributes() {
		return Map.copyOf(attributes);
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String actualType(Object value) {
		return value == null ? "null" : value.getClass().getName();
	}
}
