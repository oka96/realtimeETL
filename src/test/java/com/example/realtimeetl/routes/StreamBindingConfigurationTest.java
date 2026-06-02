package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import com.example.realtimeetl.etl.EtlRouteDefinition;
import com.example.realtimeetl.etl.EtlRouteRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.core.env.Environment;

@EnableTestBinder
@SpringBootTest
class StreamBindingConfigurationTest {

	@Autowired
	private Environment environment;

	@Autowired
	private EtlRouteRegistry registry;

	@Autowired
	private HealthContributorRegistry healthContributorRegistry;

	@Test
	void streamFunctionDefinitionsMatchRegisteredRoutes() {
		List<String> functionNames = Arrays.asList(
			environment.getRequiredProperty("spring.cloud.function.definition").split(";"));
		List<String> routeNames = registry.definitions().stream()
			.map(EtlRouteDefinition::routeName)
			.toList();

		assertThat(functionNames).containsExactlyElementsOf(routeNames);
		for (String routeName : routeNames) {
			assertBinding(routeName, "in");
			assertBinding(routeName, "out");
			assertThat(environment.getProperty("spring.cloud.stream.bindings.%s-in-0.group".formatted(routeName)))
				.isNotBlank();
		}
		assertThat(routeNames.stream()
			.map(routeName -> environment.getProperty("spring.cloud.stream.bindings.%s-out-0.destination"
				.formatted(routeName)))
			.toList())
			.doesNotHaveDuplicates();
		assertThat(routeNames.stream()
			.map(routeName -> inputConsumer(routeName))
			.toList())
			.doesNotHaveDuplicates();
	}

	@Test
	void readinessHealthIncludesEtlRoutesContributor() {
		assertThat(healthContributorRegistry.getContributor("etlRoutes")).isNotNull();
		assertThat(environment.getProperty("management.endpoint.health.probes.enabled")).isEqualTo("true");
		assertThat(environment.getProperty("management.endpoint.health.group.readiness.include"))
			.contains("etlRoutes");
		assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
			.contains("prometheus");
		assertThat(environment.getProperty("management.prometheus.metrics.export.enabled")).isEqualTo("true");
	}

	private void assertBinding(String routeName, String direction) {
		String bindingPrefix = "spring.cloud.stream.bindings.%s-%s-0".formatted(routeName, direction);

		assertThat(environment.getProperty(bindingPrefix + ".destination")).isNotBlank();
		assertThat(environment.getProperty(bindingPrefix + ".content-type")).isEqualTo("application/json");
	}

	private InputConsumer inputConsumer(String routeName) {
		String bindingPrefix = "spring.cloud.stream.bindings.%s-in-0".formatted(routeName);
		return new InputConsumer(
			environment.getProperty(bindingPrefix + ".destination"),
			environment.getProperty(bindingPrefix + ".group"));
	}

	private record InputConsumer(String destination, String group) {
	}
}
