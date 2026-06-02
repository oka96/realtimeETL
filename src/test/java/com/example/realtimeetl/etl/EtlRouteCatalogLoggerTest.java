package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class EtlRouteCatalogLoggerTest {

	@Test
	void rejectsNullRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteCatalogLogger(null, new MockEnvironment()))
			.withMessage("registry must not be null");
	}

	@Test
	void rejectsNullEnvironment() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteCatalogLogger(new EtlRouteRegistry(List.of(route("routeA"))), null))
			.withMessage("environment must not be null");
	}

	@Test
	void logsConfiguredRouteDefinitions(CapturedOutput output) {
		EtlRoute route = EtlRoute.builder("routeA")
			.payloadTypes(String.class, Integer.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "source-a",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("extract", payload -> payload)
			.transformPayload("load", payload -> payload)
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "route-a",
				EtlHeaders.EVENT_VERSION, "v1"))
			.build();
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(route));
		MockEnvironment environment = new MockEnvironment()
			.withProperty("spring.cloud.stream.bindings.routeA-in-0.destination", "route-a.input")
			.withProperty("spring.cloud.stream.bindings.routeA-in-0.group", "route-a-consumer")
			.withProperty("spring.cloud.stream.bindings.routeA-in-0.consumer.max-attempts", "4")
			.withProperty("spring.cloud.stream.bindings.routeA-in-0.consumer.back-off-initial-interval", "200")
			.withProperty("spring.cloud.stream.bindings.routeA-in-0.consumer.back-off-max-interval", "2000")
			.withProperty("spring.cloud.stream.bindings.routeA-out-0.destination", "route-a.output")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "etl.failures");

		new EtlRouteCatalogLogger(registry, environment).run(null);

		assertThat(output)
			.contains("Configured 1 ETL route(s)")
			.contains("ETL route 'routeA' maps java.lang.String -> java.lang.Integer with input contract headers ")
			.contains("etlEventType=source-a")
			.contains("output contract headers ")
			.contains("etlEventType=route-a")
			.contains("etlEventVersion=v1")
			.contains("and 4 node(s): [requireContract, extract, load, markContract]; input routeA-in-0 -> 'route-a.input' "
				+ "group 'route-a-consumer' retry maxAttempts=4 backOffInitialInterval=200 "
				+ "backOffMaxInterval=2000; "
				+ "output routeA-out-0 -> 'route-a.output'")
			.contains("ETL failure events output etlFailures-out-0 -> 'etl.failures'");
	}

	private static EtlRoute route(String name) {
		return EtlRoute.builder(name)
			.transformPayload("noop", payload -> payload)
			.build();
	}
}
