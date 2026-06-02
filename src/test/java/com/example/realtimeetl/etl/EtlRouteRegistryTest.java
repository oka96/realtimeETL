package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EtlRouteRegistryTest {

	@Test
	void indexesRoutesByName() {
		EtlRoute route = route("routeA");
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(route));

		assertThat(registry.routes()).containsExactly(route);
		assertThat(registry.required("routeA")).isSameAs(route);
	}

	@Test
	void exposesImmutableRouteDefinitions() {
		EtlRoute routeA = EtlRoute.builder("routeA")
			.payloadTypes(String.class, Integer.class)
			.transformPayload("extract", payload -> payload)
			.transformPayload("load", payload -> payload)
			.build();
		EtlRoute routeB = route("routeB");
		EtlRoute routeC = EtlRoute.builder("routeC")
			.enrichHeaders("markContract", Map.of(EtlHeaders.EVENT_TYPE, "route-c"))
			.build();
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(routeA, routeB, routeC));

		assertThat(registry.definitions())
			.containsExactly(
				new EtlRouteDefinition(
					"routeA",
					String.class.getName(),
					Integer.class.getName(),
					List.of("extract", "load"),
					Map.of(),
					Map.of()),
				new EtlRouteDefinition(
					"routeB",
					Object.class.getName(),
					Object.class.getName(),
					List.of("noop"),
					Map.of(),
					Map.of()),
				new EtlRouteDefinition(
					"routeC",
					Object.class.getName(),
					Object.class.getName(),
					List.of("markContract"),
					Map.of(),
					Map.of(EtlHeaders.EVENT_TYPE, "route-c")));
		assertThat(registry.requiredDefinition("routeA").nodeCount()).isEqualTo(2);
		assertThat(registry.requiredDefinition("routeA").inputBindingName()).isEqualTo("routeA-in-0");
		assertThat(registry.requiredDefinition("routeA").outputBindingName()).isEqualTo("routeA-out-0");
		assertThatThrownBy(() -> registry.requiredDefinition("routeA").nodeNames().add("mutate"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> registry.requiredDefinition("routeC").contractHeaders().put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> registry.requiredDefinition("routeA").inputContractHeaders().put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsDuplicateRouteNames() {
		assertThatThrownBy(() -> new EtlRouteRegistry(List.of(route("routeA"), route("routeA"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Duplicate ETL route name: routeA");
	}

	@Test
	void rejectsEmptyRouteCollection() {
		assertThatThrownBy(() -> new EtlRouteRegistry(List.of()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("At least one ETL route must be registered");
	}

	@Test
	void rejectsNullRouteCollection() {
		assertThatThrownBy(() -> new EtlRouteRegistry(null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("routes must not be null");
	}

	@Test
	void rejectsNullRouteEntry() {
		assertThatThrownBy(() -> new EtlRouteRegistry(java.util.Arrays.asList(route("routeA"), null)))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("route must not be null");
	}

	@Test
	void rejectsUnknownRouteLookup() {
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(route("routeA")));

		assertThatThrownBy(() -> registry.required("missing"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unknown ETL route: missing");
	}

	@Test
	void rejectsBlankRouteLookupName() {
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(route("routeA")));

		assertThatThrownBy(() -> registry.required(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
		assertThatThrownBy(() -> registry.required(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
	}

	@Test
	void rejectsBlankRouteDefinitionLookupName() {
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(route("routeA")));

		assertThatThrownBy(() -> registry.requiredDefinition(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
		assertThatThrownBy(() -> registry.requiredDefinition(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
	}

	private static EtlRoute route(String name) {
		return EtlRoute.builder(name)
			.transformPayload("noop", payload -> payload)
			.build();
	}
}
