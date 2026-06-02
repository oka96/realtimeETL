package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class EtlRouteCatalogMetricsTest {

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final EtlRouteRegistry registry = new EtlRouteRegistry(List.of(route("routeA"), route("routeB")));

	@Test
	void registersRouteCountAndNodeCountGauges() {
		new EtlRouteCatalogMetrics(registry).bindTo(meterRegistry);

		assertThat(gauge(EtlRouteCatalogMetrics.ROUTE_COUNT_GAUGE).value()).isEqualTo(2.0);
		assertThat(routeNodeGauge("routeA").value()).isEqualTo(2.0);
		assertThat(routeNodeGauge("routeB").value()).isEqualTo(1.0);
	}

	@Test
	void rejectsMissingRouteRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteCatalogMetrics(null))
			.withMessage("registry must not be null");
	}

	@Test
	void rejectsMissingMeterRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteCatalogMetrics(registry).bindTo(null))
			.withMessage("meterRegistry must not be null");
	}

	private static EtlRoute route(String routeName) {
		if ("routeA".equals(routeName)) {
			return EtlRoute.builder(routeName)
				.transformPayload("extract", payload -> payload)
				.transformPayload("load", payload -> payload)
				.build();
		}
		return EtlRoute.builder(routeName)
			.transformPayload("onlyNode", payload -> payload)
			.build();
	}

	private Gauge gauge(String name) {
		Gauge gauge = meterRegistry.find(name).gauge();
		assertThat(gauge).isNotNull();
		return gauge;
	}

	private Gauge routeNodeGauge(String routeName) {
		Gauge gauge = meterRegistry.find(EtlRouteCatalogMetrics.ROUTE_NODE_COUNT_GAUGE)
			.tag("route", routeName)
			.gauge();
		assertThat(gauge).isNotNull();
		return gauge;
	}
}
