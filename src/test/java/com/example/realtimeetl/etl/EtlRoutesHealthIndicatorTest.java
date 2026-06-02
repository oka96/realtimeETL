package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;

class EtlRoutesHealthIndicatorTest {

	@Test
	void rejectsNullRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRoutesHealthIndicator(null, new EtlRouteCatalogDetails(environment())))
			.withMessage("registry must not be null");
	}

	@Test
	void rejectsNullCatalogDetails() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRoutesHealthIndicator(new EtlRouteRegistry(List.of(route("healthRoute"))), null))
			.withMessage("catalogDetails must not be null");
	}

	@Test
	@SuppressWarnings("deprecation")
	void reportsConfiguredRouteContracts() {
		EtlRoute route = EtlRoute.builder("healthRoute")
			.payloadTypes(String.class, Integer.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "source-health",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("parseLength", String.class, Integer.class, String::length)
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "health-event",
				EtlHeaders.EVENT_VERSION, "v1"))
			.build();
		EtlRoutesHealthIndicator indicator = new EtlRoutesHealthIndicator(
			new EtlRouteRegistry(List.of(route)),
			new EtlRouteCatalogDetails(environment()));

		Health health = indicator.health();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("routeCount", 1);
		assertThat(health.getDetails().get("failureBinding"))
			.isEqualTo(Map.of(
				"name", EtlFailureEventPublisher.BINDING_NAME,
				"destination", "etl.failures",
				"contentType", "application/json",
				"payloadType", EtlFailureEvent.class.getName(),
				"contractHeaders", Map.of(
					EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE,
					EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)));
		assertThat(health.getDetails().get("routes"))
			.asList()
			.singleElement()
			.satisfies(routeDetails -> {
				@SuppressWarnings("unchecked")
				Map<String, Object> details = (Map<String, Object>) routeDetails;
					assertThat(details)
						.containsEntry("routeName", "healthRoute")
						.containsEntry("inputType", String.class.getName())
						.containsEntry("outputType", Integer.class.getName())
						.containsEntry("nodeCount", 3)
						.containsEntry("nodes", List.of("requireContract", "parseLength", "markContract"))
						.containsEntry("inputContractHeaders", Map.of(
							EtlHeaders.EVENT_TYPE, "source-health",
							EtlHeaders.EVENT_VERSION, "v1"))
						.containsEntry("contractHeaders", Map.of(
							EtlHeaders.EVENT_TYPE, "health-event",
							EtlHeaders.EVENT_VERSION, "v1"));
					@SuppressWarnings("unchecked")
					Map<String, Object> inputBinding = (Map<String, Object>) details.get("inputBinding");
					@SuppressWarnings("unchecked")
					Map<String, Object> outputBinding = (Map<String, Object>) details.get("outputBinding");
					assertThat(inputBinding)
						.containsEntry("name", "healthRoute-in-0")
						.containsEntry("destination", "health.input")
						.containsEntry("contentType", "application/json")
						.containsEntry("group", "health-consumer");
					assertThat(inputBinding.get("retry"))
						.isEqualTo(Map.of(
							"maxAttempts", 5,
							"backOffInitialInterval", 250,
							"backOffMaxInterval", 2_500));
					assertThat(outputBinding)
						.containsEntry("name", "healthRoute-out-0")
						.containsEntry("destination", "health.output")
						.containsEntry("contentType", "application/json");
			});
	}

	private static MockEnvironment environment() {
		return new MockEnvironment()
			.withProperty("spring.cloud.stream.bindings.healthRoute-in-0.destination", "health.input")
			.withProperty("spring.cloud.stream.bindings.healthRoute-in-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.healthRoute-in-0.group", "health-consumer")
			.withProperty("spring.cloud.stream.bindings.healthRoute-in-0.consumer.max-attempts", "5")
			.withProperty("spring.cloud.stream.bindings.healthRoute-in-0.consumer.back-off-initial-interval", "250")
			.withProperty("spring.cloud.stream.bindings.healthRoute-in-0.consumer.back-off-max-interval", "2500")
			.withProperty("spring.cloud.stream.bindings.healthRoute-out-0.destination", "health.output")
			.withProperty("spring.cloud.stream.bindings.healthRoute-out-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "etl.failures")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", "application/json");
	}

	private static EtlRoute route(String name) {
		return EtlRoute.builder(name)
			.transformPayload("noop", payload -> payload)
			.build();
	}
}
