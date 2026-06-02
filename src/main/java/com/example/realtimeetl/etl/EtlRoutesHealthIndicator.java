package com.example.realtimeetl.etl;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class EtlRoutesHealthIndicator implements HealthIndicator {

	private final EtlRouteRegistry registry;
	private final EtlRouteCatalogDetails catalogDetails;

	public EtlRoutesHealthIndicator(EtlRouteRegistry registry, EtlRouteCatalogDetails catalogDetails) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.catalogDetails = Objects.requireNonNull(catalogDetails, "catalogDetails must not be null");
	}

	@Override
	public Health health() {
		List<EtlRouteDefinition> definitions = registry.definitions();
		if (definitions.isEmpty()) {
			return Health.down()
				.withDetail("failureBinding", catalogDetails.failureBindingDetails())
				.withDetail("routeCount", 0)
				.withDetail("routes", List.of())
				.build();
		}

		return Health.up()
			.withDetail("failureBinding", catalogDetails.failureBindingDetails())
			.withDetail("routeCount", definitions.size())
			.withDetail("routes", definitions.stream()
				.map(catalogDetails::routeDetails)
				.toList())
			.build();
	}
}
