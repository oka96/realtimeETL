package com.example.realtimeetl.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class EtlRouteCatalogDetails {

	private final Environment environment;

	public EtlRouteCatalogDetails(Environment environment) {
		this.environment = Objects.requireNonNull(environment, "environment must not be null");
	}

	public Map<String, Object> routeDetails(EtlRouteDefinition definition) {
		Objects.requireNonNull(definition, "definition must not be null");
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("routeName", definition.routeName());
		details.put("inputBinding", bindingDetails(definition.inputBindingName(), true));
		details.put("outputBinding", bindingDetails(definition.outputBindingName(), false));
		details.put("inputType", definition.inputType());
		details.put("outputType", definition.outputType());
		details.put("inputContractHeaders", definition.inputContractHeaders());
		details.put("contractHeaders", definition.contractHeaders());
		details.put("nodeCount", definition.nodeCount());
		details.put("nodes", definition.nodeNames());
		return immutableDetails(details);
	}

	public Map<String, Object> failureBindingDetails() {
		Map<String, Object> details = new LinkedHashMap<>(bindingDetails(EtlFailureEventPublisher.BINDING_NAME, false));
		details.put("payloadType", EtlFailureEvent.class.getName());
		details.put("contractHeaders", Map.of(
			EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE,
			EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION));
		return immutableDetails(details);
	}

	private Map<String, Object> bindingDetails(String bindingName, boolean input) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", bindingName);
		details.put("destination", environment.getProperty(bindingProperty(bindingName, "destination")));
		details.put("contentType", environment.getProperty(bindingProperty(bindingName, "content-type")));
		if (input) {
			details.put("group", environment.getProperty(bindingProperty(bindingName, "group")));
			details.put("retry", retryDetails(bindingName));
		}
		return immutableDetails(details);
	}

	private Map<String, Object> retryDetails(String bindingName) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("maxAttempts", environment.getProperty(consumerProperty(bindingName, "max-attempts"), Integer.class));
		details.put(
			"backOffInitialInterval",
			environment.getProperty(consumerProperty(bindingName, "back-off-initial-interval"), Integer.class));
		details.put(
			"backOffMaxInterval",
			environment.getProperty(consumerProperty(bindingName, "back-off-max-interval"), Integer.class));
		return immutableDetails(details);
	}

	private static String bindingProperty(String bindingName, String propertyName) {
		return "spring.cloud.stream.bindings.%s.%s".formatted(bindingName, propertyName);
	}

	private static String consumerProperty(String bindingName, String propertyName) {
		return bindingProperty(bindingName, "consumer." + propertyName);
	}

	private static Map<String, Object> immutableDetails(Map<String, Object> details) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(details));
	}
}
