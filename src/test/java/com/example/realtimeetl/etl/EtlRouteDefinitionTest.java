package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EtlRouteDefinitionTest {

	@Test
	void exposesBindingNamesAndImmutableMetadata() {
		EtlRouteDefinition definition = new EtlRouteDefinition(
			"orderInvoice",
			"com.example.OrderCreated",
			"com.example.InvoiceEvent",
			List.of("validateOrder", "buildInvoice"),
			Map.of(EtlHeaders.EVENT_TYPE, "order-created"),
			Map.of(EtlHeaders.EVENT_TYPE, "invoice"));

		assertThat(definition.inputBindingName()).isEqualTo("orderInvoice-in-0");
		assertThat(definition.outputBindingName()).isEqualTo("orderInvoice-out-0");
		assertThat(definition.nodeCount()).isEqualTo(2);
		assertThatThrownBy(() -> definition.nodeNames().add("mutate"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> definition.inputContractHeaders().put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> definition.contractHeaders().put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsBlankIdentityAndTypeMetadata() {
		assertThatThrownBy(() -> definition(" ", "input.Type", "output.Type", List.of("nodeA")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
		assertThatThrownBy(() -> definition("routeA", " ", "output.Type", List.of("nodeA")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputType must not be blank");
		assertThatThrownBy(() -> definition("routeA", "input.Type", " ", List.of("nodeA")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("outputType must not be blank");
	}

	@Test
	void rejectsInvalidRouteNameMetadata() {
		assertThatThrownBy(() -> definition("1badRoute", "input.Type", "output.Type", List.of("nodeA")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		assertThatThrownBy(() -> definition("bad route", "input.Type", "output.Type", List.of("nodeA")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route name must start with a letter and contain only letters, numbers, underscores, or hyphens");
	}

	@Test
	void rejectsMissingMetadataCollections() {
		assertThatNullPointerException()
			.isThrownBy(() -> definition("routeA", "input.Type", "output.Type", null))
			.withMessage("nodeNames must not be null");
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteDefinition(
				"routeA",
				"input.Type",
				"output.Type",
				List.of("nodeA"),
				null,
				Map.of()))
			.withMessage("inputContractHeaders must not be null");
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteDefinition(
				"routeA",
				"input.Type",
				"output.Type",
				List.of("nodeA"),
				Map.of(),
				null))
			.withMessage("contractHeaders must not be null");
	}

	@Test
	void rejectsEmptyOrBlankNodeNames() {
		assertThatThrownBy(() -> definition("routeA", "input.Type", "output.Type", List.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("nodeNames must not be empty");
		assertThatThrownBy(() -> definition("routeA", "input.Type", "output.Type", List.of(" ")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node names must not be blank");
		assertThatThrownBy(() -> definition("routeA", "input.Type", "output.Type", Arrays.asList("nodeA", null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("node names must not be blank");
	}

	@Test
	void rejectsInvalidOrDuplicateNodeNameMetadata() {
		assertThatThrownBy(() -> definition("routeA", "input.Type", "output.Type", List.of("1badNode")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Node name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		assertThatThrownBy(() -> definition("routeA", "input.Type", "output.Type", List.of("bad node")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Node name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		assertThatThrownBy(() -> definition("routeA", "input.Type", "output.Type", List.of("nodeA", "nodeA")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route definition 'routeA' contains duplicate node name 'nodeA'");
	}

	@Test
	void rejectsBlankOrNullContractHeaderMetadata() {
		Map<String, Object> blankHeaderName = new LinkedHashMap<>();
		blankHeaderName.put(" ", "value");
		assertThatThrownBy(() -> new EtlRouteDefinition(
			"routeA",
			"input.Type",
			"output.Type",
			List.of("nodeA"),
			blankHeaderName,
			Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputContractHeaders header names must not be blank");

		Map<String, Object> nullHeaderValue = new LinkedHashMap<>();
		nullHeaderValue.put(EtlHeaders.EVENT_TYPE, null);
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteDefinition(
				"routeA",
				"input.Type",
				"output.Type",
				List.of("nodeA"),
				Map.of(),
				nullHeaderValue))
			.withMessage("contractHeaders header value for 'etlEventType' must not be null");
	}

	private static EtlRouteDefinition definition(
		String routeName,
		String inputType,
		String outputType,
		List<String> nodeNames) {
		return new EtlRouteDefinition(routeName, inputType, outputType, nodeNames, Map.of(), Map.of());
	}
}
