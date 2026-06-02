package com.example.realtimeetl.etl;

import java.util.Objects;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

@Component
public final class EtlRouteCatalogMetrics implements MeterBinder {

	public static final String ROUTE_COUNT_GAUGE = "realtime.etl.routes.configured";
	public static final String ROUTE_NODE_COUNT_GAUGE = "realtime.etl.route.nodes";

	private static final String ROUTE_TAG = "route";

	private final EtlRouteRegistry registry;

	public EtlRouteCatalogMetrics(EtlRouteRegistry registry) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
	}

	@Override
	public void bindTo(MeterRegistry meterRegistry) {
		Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
		Gauge.builder(ROUTE_COUNT_GAUGE, registry, routeRegistry -> routeRegistry.definitions().size())
			.description("Number of configured ETL routes")
			.register(meterRegistry);
		registry.definitions().forEach(definition ->
			Gauge.builder(
				ROUTE_NODE_COUNT_GAUGE,
				registry,
				routeRegistry -> routeRegistry.requiredDefinition(definition.routeName()).nodeCount())
				.description("Number of configured nodes in an ETL route")
				.tag(ROUTE_TAG, definition.routeName())
				.register(meterRegistry));
	}
}
