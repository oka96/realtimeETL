package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EtlRouteCatalogDetailsTest {

	@Test
	void rejectsNullEnvironment() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteCatalogDetails(null))
			.withMessage("environment must not be null");
	}

	@Test
	void rejectsNullRouteDefinition() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteCatalogDetails(new MockEnvironment()).routeDetails(null))
			.withMessage("definition must not be null");
	}

	@Test
	void returnsImmutableRouteDetails() {
		EtlRouteCatalogDetails catalogDetails = new EtlRouteCatalogDetails(environment());

		Map<String, Object> details = catalogDetails.routeDetails(definition());
		@SuppressWarnings("unchecked")
		Map<String, Object> inputBinding = (Map<String, Object>) details.get("inputBinding");
		@SuppressWarnings("unchecked")
		Map<String, Object> retry = (Map<String, Object>) inputBinding.get("retry");
		@SuppressWarnings("unchecked")
		Map<String, Object> outputBinding = (Map<String, Object>) details.get("outputBinding");

		assertThatThrownBy(() -> details.put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> inputBinding.put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> retry.put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> outputBinding.put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void returnsImmutableFailureBindingDetails() {
		EtlRouteCatalogDetails catalogDetails = new EtlRouteCatalogDetails(environment());

		Map<String, Object> details = catalogDetails.failureBindingDetails();

		assertThatThrownBy(() -> details.put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	private static EtlRouteDefinition definition() {
		return new EtlRouteDefinition(
			"catalogRoute",
			String.class.getName(),
			Integer.class.getName(),
			List.of("nodeA"),
			Map.of(EtlHeaders.EVENT_TYPE, "source-event"),
			Map.of(EtlHeaders.EVENT_TYPE, "output-event"));
	}

	private static MockEnvironment environment() {
		return new MockEnvironment()
			.withProperty("spring.cloud.stream.bindings.catalogRoute-in-0.destination", "catalog.input")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-in-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-in-0.group", "catalog-consumer")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-in-0.consumer.max-attempts", "3")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-in-0.consumer.back-off-initial-interval", "500")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-in-0.consumer.back-off-max-interval", "5000")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-out-0.destination", "catalog.output")
			.withProperty("spring.cloud.stream.bindings.catalogRoute-out-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "etl.failures")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", "application/json");
	}
}
