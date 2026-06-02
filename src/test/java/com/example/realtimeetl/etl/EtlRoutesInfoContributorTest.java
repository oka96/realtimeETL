package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

class EtlRoutesInfoContributorTest {

	@Test
	void rejectsNullRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRoutesInfoContributor(null, new EtlRouteCatalogDetails(environment())))
			.withMessage("registry must not be null");
	}

	@Test
	void rejectsNullCatalogDetails() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRoutesInfoContributor(new EtlRouteRegistry(List.of(route("infoRoute"))), null))
			.withMessage("catalogDetails must not be null");
	}

	@Test
	void rejectsNullInfoBuilder() {
		EtlRoutesInfoContributor contributor = new EtlRoutesInfoContributor(
			new EtlRouteRegistry(List.of(route("infoRoute"))),
			new EtlRouteCatalogDetails(environment()));

		assertThatNullPointerException()
			.isThrownBy(() -> contributor.contribute(null))
			.withMessage("builder must not be null");
	}

	@Test
	@SuppressWarnings("deprecation")
	void contributesRouteCatalogToActuatorInfo() {
		EtlRoute route = EtlRoute.builder("infoRoute")
			.payloadTypes(String.class, Integer.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "source-info",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("parseLength", String.class, Integer.class, String::length)
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "info-event",
				EtlHeaders.EVENT_VERSION, "v1"))
			.build();
		EtlRoutesInfoContributor contributor = new EtlRoutesInfoContributor(
			new EtlRouteRegistry(List.of(route)),
			new EtlRouteCatalogDetails(environment()));
		Info.Builder builder = new Info.Builder();

		contributor.contribute(builder);

		@SuppressWarnings("unchecked")
		Map<String, Object> etl = (Map<String, Object>) builder.build().getDetails().get("etl");
		assertThat(etl).containsEntry("routeCount", 1);
		assertThat(etl.get("failureBinding"))
			.isEqualTo(Map.of(
				"name", EtlFailureEventPublisher.BINDING_NAME,
				"destination", "etl.failures",
				"contentType", "application/json",
				"payloadType", EtlFailureEvent.class.getName(),
				"contractHeaders", Map.of(
					EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE,
					EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)));
		assertThat(etl.get("routes"))
			.asList()
			.singleElement()
			.satisfies(routeDetails -> {
				@SuppressWarnings("unchecked")
				Map<String, Object> details = (Map<String, Object>) routeDetails;
					assertThat(details)
						.containsEntry("routeName", "infoRoute")
						.containsEntry("inputType", String.class.getName())
						.containsEntry("outputType", Integer.class.getName())
						.containsEntry("nodeCount", 3)
						.containsEntry("nodes", List.of("requireContract", "parseLength", "markContract"))
						.containsEntry("inputContractHeaders", Map.of(
							EtlHeaders.EVENT_TYPE, "source-info",
							EtlHeaders.EVENT_VERSION, "v1"))
						.containsEntry("contractHeaders", Map.of(
							EtlHeaders.EVENT_TYPE, "info-event",
							EtlHeaders.EVENT_VERSION, "v1"));
					@SuppressWarnings("unchecked")
					Map<String, Object> inputBinding = (Map<String, Object>) details.get("inputBinding");
					@SuppressWarnings("unchecked")
					Map<String, Object> outputBinding = (Map<String, Object>) details.get("outputBinding");
					assertThat(inputBinding)
						.containsEntry("name", "infoRoute-in-0")
						.containsEntry("destination", "info.input")
						.containsEntry("contentType", "application/json")
						.containsEntry("group", "info-consumer");
					assertThat(inputBinding.get("retry"))
						.isEqualTo(Map.of(
							"maxAttempts", 7,
							"backOffInitialInterval", 300,
							"backOffMaxInterval", 3_000));
					assertThat(outputBinding)
						.containsEntry("name", "infoRoute-out-0")
						.containsEntry("destination", "info.output")
						.containsEntry("contentType", "application/json");
			});
	}

	private static MockEnvironment environment() {
		return new MockEnvironment()
			.withProperty("spring.cloud.stream.bindings.infoRoute-in-0.destination", "info.input")
			.withProperty("spring.cloud.stream.bindings.infoRoute-in-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.infoRoute-in-0.group", "info-consumer")
			.withProperty("spring.cloud.stream.bindings.infoRoute-in-0.consumer.max-attempts", "7")
			.withProperty("spring.cloud.stream.bindings.infoRoute-in-0.consumer.back-off-initial-interval", "300")
			.withProperty("spring.cloud.stream.bindings.infoRoute-in-0.consumer.back-off-max-interval", "3000")
			.withProperty("spring.cloud.stream.bindings.infoRoute-out-0.destination", "info.output")
			.withProperty("spring.cloud.stream.bindings.infoRoute-out-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "etl.failures")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", "application/json");
	}

	private static EtlRoute route(String name) {
		return EtlRoute.builder(name)
			.transformPayload("noop", payload -> payload)
			.build();
	}
}
