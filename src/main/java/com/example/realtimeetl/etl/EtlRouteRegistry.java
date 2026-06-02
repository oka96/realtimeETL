package com.example.realtimeetl.etl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

@Component
public final class EtlRouteRegistry {

	private final Map<String, EtlRoute> routes;

	public EtlRouteRegistry(Collection<EtlRoute> routes) {
		Objects.requireNonNull(routes, "routes must not be null");
		if (routes.isEmpty()) {
			throw new IllegalStateException("At least one ETL route must be registered");
		}
		Map<String, EtlRoute> indexed = new LinkedHashMap<>();
		for (EtlRoute route : routes) {
			Objects.requireNonNull(route, "route must not be null");
			if (indexed.putIfAbsent(route.name(), route) != null) {
				throw new IllegalStateException("Duplicate ETL route name: " + route.name());
			}
		}
		this.routes = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
	}

	public Collection<EtlRoute> routes() {
		return routes.values();
	}

	public List<EtlRouteDefinition> definitions() {
		return routes.values().stream()
			.map(route -> new EtlRouteDefinition(
				route.name(),
				route.inputType().getName(),
				route.outputType().getName(),
				route.nodes().stream()
					.map(NamedEtlNode::name)
					.toList(),
				route.inputContractHeaders(),
				route.contractHeaders()))
			.toList();
	}

	public EtlRouteDefinition requiredDefinition(String name) {
		EtlRoute route = required(name);
		return new EtlRouteDefinition(
			route.name(),
			route.inputType().getName(),
			route.outputType().getName(),
			route.nodes().stream()
				.map(NamedEtlNode::name)
				.toList(),
			route.inputContractHeaders(),
			route.contractHeaders());
	}

	public EtlRoute required(String name) {
		String routeName = requireText(name);
		EtlRoute route = routes.get(routeName);
		if (route == null) {
			throw new IllegalArgumentException("Unknown ETL route: " + routeName);
		}
		return route;
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("routeName must not be blank");
		}
		return value;
	}
}
