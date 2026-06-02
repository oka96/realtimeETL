package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class EtlRouteTest {

	@Test
	void builderExecutesCustomNodesInOrder() {
		EtlRoute route = EtlRoute.builder("customRoute")
			.node("captureOriginal", (message, context) -> {
				context.put("original", message.getPayload());
				return message;
			})
			.transformPayload("uppercase", payload -> ((String) payload).toUpperCase())
			.node("appendOriginal", (message, context) -> EtlMessages.replacePayload(
				message,
				message.getPayload() + ":" + context.get("original").orElseThrow()))
			.build();

		Message<?> input = MessageBuilder.withPayload("abc").build();
		Message<?> output = route.execute(input);

		assertThat(output.getPayload()).isEqualTo("ABC:abc");
		assertThat(output.getHeaders())
			.containsEntry(EtlHeaders.ROUTE, "customRoute")
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, String.class.getName())
			.containsEntry(EtlHeaders.OUTPUT_PAYLOAD_TYPE, String.class.getName())
			.containsEntry(EtlHeaders.LAST_NODE, "appendOriginal")
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_ID, input.getHeaders().getId().toString())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, input.getHeaders().getTimestamp())
			.containsEntry(EtlHeaders.NODE_COUNT, 3);
		assertThat((String) output.getHeaders().get(EtlHeaders.TRACE_ID)).isNotBlank();
		assertThat((Long) output.getHeaders().get(EtlHeaders.DURATION_NANOS)).isPositive();
		assertThat(nodeDurations(output))
			.containsOnlyKeys("captureOriginal", "uppercase", "appendOriginal")
			.allSatisfy((nodeName, durationNanos) -> assertThat(durationNanos).isNotNegative());
		assertThat(route.nodes())
			.extracting(NamedEtlNode::name)
			.containsExactly("captureOriginal", "uppercase", "appendOriginal");
	}

	@Test
	void wrapsNodeFailuresWithRouteAndNodeContext() {
		EtlRoute route = EtlRoute.builder("failureRoute")
			.node("explode", (message, context) -> {
				throw new IllegalArgumentException("invalid payload");
			})
			.build();
		Message<?> input = MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-failure-1")
			.build();

		assertThatThrownBy(() -> route.execute(input))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("failureRoute");
				assertThat(exception.nodeName()).isEqualTo("explode");
				assertThat(exception.traceId()).isEqualTo("trace-failure-1");
				assertThat(exception.inputPayloadType()).isEqualTo(String.class.getName());
				assertThat(exception.sourceMessageId()).isEqualTo(input.getHeaders().getId().toString());
				assertThat(exception.sourceMessageTimestamp()).isEqualTo(input.getHeaders().getTimestamp());
				assertThat(exception.completedNodeCount()).isZero();
				assertThat(exception.nodeDurationsNanos()).containsOnlyKeys("explode");
				assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
			})
			.hasMessage("Route 'failureRoute' failed at node 'explode' for trace 'trace-failure-1': invalid payload");
	}

	@Test
	void enrichesNodeThrownRouteFailuresWithCurrentRouteContext() {
		EtlRoute route = EtlRoute.builder("outerRoute")
			.transformPayload("prepare", payload -> payload)
			.node("delegate", (message, context) -> {
				throw new EtlRouteExecutionException(
					"innerRoute",
					"innerNode",
					new IllegalArgumentException("inner rejected payload"));
			})
			.build();
		Message<?> input = MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-delegate-1")
			.build();

		assertThatThrownBy(() -> route.execute(input))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("outerRoute");
				assertThat(exception.nodeName()).isEqualTo("delegate");
				assertThat(exception.traceId()).isEqualTo("trace-delegate-1");
				assertThat(exception.inputPayloadType()).isEqualTo(String.class.getName());
				assertThat(exception.sourceMessageId()).isEqualTo(input.getHeaders().getId().toString());
				assertThat(exception.sourceMessageTimestamp()).isEqualTo(input.getHeaders().getTimestamp());
				assertThat(exception.completedNodeCount()).isEqualTo(1);
				assertThat(exception.nodeDurationsNanos()).containsOnlyKeys("prepare", "delegate");
				assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);

				EtlFailureEvent failureEvent = EtlFailureEvent.from(exception, 1_780_000_000_500L);
				assertThat(failureEvent.routeName()).isEqualTo("outerRoute");
				assertThat(failureEvent.nodeName()).isEqualTo("delegate");
				assertThat(failureEvent.traceId()).isEqualTo("trace-delegate-1");
				assertThat(failureEvent.sourceMessageId()).isEqualTo(input.getHeaders().getId().toString());
			})
			.hasMessage("Route 'outerRoute' failed at node 'delegate' for trace 'trace-delegate-1': "
				+ "inner rejected payload");
	}

	@Test
	void nodeFailureDiagnosticsIncludeCompletedNodeCountAndDurations() {
		EtlRoute route = EtlRoute.builder("failureRoute")
			.transformPayload("first", payload -> payload)
			.node("explode", (message, context) -> {
				throw new IllegalStateException("boom");
			})
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc").build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.completedNodeCount()).isEqualTo(1);
				assertThat(exception.nodeDurationsNanos()).containsOnlyKeys("first", "explode");
				assertThat(exception.nodeDurationsNanos())
					.allSatisfy((nodeName, durationNanos) -> assertThat(durationNanos).isNotNegative());
				assertThatThrownBy(() -> exception.nodeDurationsNanos().put("mutate", 1L))
					.isInstanceOf(UnsupportedOperationException.class);
			});
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void wrapsMismatchedFunctionInputWithRouteContext() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(Integer.class, Integer.class)
			.transformPayload("noop", payload -> payload)
			.build();
		Function<Message, Message> function = (Function) route.toFunction(Integer.class, Integer.class);
		Message<?> input = MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-function-input-1")
			.build();

		assertThatThrownBy(() -> function.apply(input))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("typedRoute");
				assertThat(exception.nodeName()).isEqualTo("validateInputPayload");
				assertThat(exception.traceId()).isEqualTo("trace-function-input-1");
				assertThat(exception.inputPayloadType()).isEqualTo(String.class.getName());
				assertThat(exception.sourceMessageId()).isEqualTo(input.getHeaders().getId().toString());
				assertThat(exception.sourceMessageTimestamp()).isEqualTo(input.getHeaders().getTimestamp());
				assertThat(exception.completedNodeCount()).isZero();
				assertThat(exception.nodeDurationsNanos()).isEmpty();
			})
			.hasMessage("Route 'typedRoute' failed at node 'validateInputPayload' "
				+ "for trace 'trace-function-input-1': "
				+ "Route expected input payload type java.lang.Integer but received java.lang.String");
	}

	@Test
	void rejectsFunctionCreationWhenRequestedTypesDoNotMatchRouteDeclaration() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(String.class, Integer.class)
			.transformPayload("noop", payload -> payload)
			.build();

		assertThatThrownBy(() -> route.toFunction(Integer.class, Integer.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("declared as java.lang.String -> java.lang.Integer")
			.hasMessageContaining("function requested java.lang.Integer -> java.lang.Integer");
	}

	@Test
	void typedTransformsCanUsePayloadAndRouteContext() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(String.class, Integer.class)
			.node("captureLength", (message, context) -> {
				context.put("length", ((String) message.getPayload()).length());
				return message;
			})
			.transformPayload(
				"combineLength",
				String.class,
				Integer.class,
				(payload, context) -> payload.length() + (int) context.get("length").orElseThrow())
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abcd").build());

		assertThat(output.getPayload()).isEqualTo(8);
		assertThat(output.getHeaders()).containsEntry(EtlHeaders.LAST_NODE, "combineLength");
	}

	@Test
	void typedTransformInputMismatchIsWrappedWithNodeContext() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(Object.class, Object.class)
			.transformPayload("requiresInteger", Integer.class, String.class, Object::toString)
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-node-input-1")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("typedRoute");
				assertThat(exception.nodeName()).isEqualTo("requiresInteger");
				assertThat(exception.traceId()).isEqualTo("trace-node-input-1");
			})
			.hasMessage("Route 'typedRoute' failed at node 'requiresInteger' for trace 'trace-node-input-1': "
				+ "Node 'requiresInteger' expected payload type java.lang.Integer but received java.lang.String");
	}

	@Test
	void typedTransformOutputMismatchIsWrappedWithNodeContext() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(String.class, Object.class)
			.transformPayload("returnsNull", String.class, Integer.class, payload -> null)
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-node-output-1")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("typedRoute");
				assertThat(exception.nodeName()).isEqualTo("returnsNull");
				assertThat(exception.traceId()).isEqualTo("trace-node-output-1");
			})
			.hasMessage("Route 'typedRoute' failed at node 'returnsNull' for trace 'trace-node-output-1': "
				+ "Node 'returnsNull' produced payload type null but expected java.lang.Integer");
	}

	@Test
	void directExecutionWrapsMismatchedDeclaredInputTypeWithRouteContext() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(Integer.class, Integer.class)
			.transformPayload("noop", payload -> payload)
			.build();
		Message<?> input = MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-route-input-1")
			.build();

		assertThatThrownBy(() -> route.execute(input))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("typedRoute");
				assertThat(exception.nodeName()).isEqualTo("validateInputPayload");
				assertThat(exception.traceId()).isEqualTo("trace-route-input-1");
				assertThat(exception.inputPayloadType()).isEqualTo(String.class.getName());
				assertThat(exception.sourceMessageId()).isEqualTo(input.getHeaders().getId().toString());
				assertThat(exception.sourceMessageTimestamp()).isEqualTo(input.getHeaders().getTimestamp());
				assertThat(exception.completedNodeCount()).isZero();
				assertThat(exception.nodeDurationsNanos()).isEmpty();
				assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
			})
			.hasMessage("Route 'typedRoute' failed at node 'validateInputPayload' "
				+ "for trace 'trace-route-input-1': "
				+ "Route expected input payload type java.lang.Integer but received java.lang.String");
	}

	@Test
	void directExecutionWrapsMismatchedDeclaredOutputTypeWithLastNodeContext() {
		EtlRoute route = EtlRoute.builder("typedRoute")
			.payloadTypes(String.class, Integer.class)
			.transformPayload("badOutput", payload -> payload)
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-output-1")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("typedRoute");
				assertThat(exception.nodeName()).isEqualTo("badOutput");
				assertThat(exception.traceId()).isEqualTo("trace-output-1");
				assertThat(exception.completedNodeCount()).isEqualTo(1);
				assertThat(exception.nodeDurationsNanos()).containsOnlyKeys("badOutput");
				assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
			})
			.hasMessage("Route 'typedRoute' failed at node 'badOutput' for trace 'trace-output-1': "
				+ "Route expected output payload type java.lang.Integer but produced java.lang.String");
	}

	@Test
	void rejectsPrimitivePayloadTypes() {
		assertThatThrownBy(() -> EtlRoute.builder("typedRoute")
			.payloadTypes(int.class, Integer.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Payload types must be reference types");
		assertThatThrownBy(() -> EtlRoute.builder("typedRoute")
			.payloadTypes(Integer.class, int.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Payload types must be reference types");
	}

	@Test
	void rejectsInvalidRouteNames() {
		assertThatThrownBy(() -> EtlRoute.builder("1badRoute")
			.transformPayload("noop", payload -> payload)
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route name must start with a letter and contain only letters, numbers, underscores, or hyphens");

		assertThatThrownBy(() -> EtlRoute.builder("bad route")
			.transformPayload("noop", payload -> payload)
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route name must start with a letter and contain only letters, numbers, underscores, or hyphens");
	}

	@Test
	void rejectsInvalidNodeNames() {
		assertThatThrownBy(() -> EtlRoute.builder("validRoute")
			.transformPayload("1badNode", payload -> payload))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Node name must start with a letter and contain only letters, numbers, underscores, or hyphens");

		assertThatThrownBy(() -> EtlRoute.builder("validRoute")
			.transformPayload("bad node", payload -> payload))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Node name must start with a letter and contain only letters, numbers, underscores, or hyphens");
	}

	@Test
	void wrapsNullNodeOutputWithRouteAndNodeContext() {
		EtlRoute route = EtlRoute.builder("nullRoute")
			.node("badNode", (message, context) -> null)
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc").build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("nullRoute");
				assertThat(exception.nodeName()).isEqualTo("badNode");
				assertThat(exception.traceId()).isNotBlank();
			})
			.hasMessageContaining("Route 'nullRoute' failed at node 'badNode' for trace '")
			.hasMessageContaining("': Node returned null");
	}

	@Test
	void rejectsDuplicateNodeNames() {
		assertThatThrownBy(() -> EtlRoute.builder("duplicateRoute")
			.transformPayload("sameName", payload -> payload)
			.transformPayload("sameName", payload -> payload)
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route 'duplicateRoute' contains duplicate node name 'sameName'");
	}

	@Test
	void wrapsNullEnrichedHeaderValuesWithRouteAndNodeContext() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeader("setHeader", "eventType", message -> null)
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-header-1")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("headerRoute");
				assertThat(exception.nodeName()).isEqualTo("setHeader");
				assertThat(exception.traceId()).isEqualTo("trace-header-1");
				assertThat(exception.getCause()).isInstanceOf(NullPointerException.class);
			})
			.hasMessage("Route 'headerRoute' failed at node 'setHeader' for trace 'trace-header-1': "
				+ "Header value for 'eventType' must not be null");
	}

	@Test
	void rejectsCustomEnrichmentOfRouteManagedHeaders() {
		assertThatThrownBy(() -> EtlRoute.builder("headerRoute")
			.enrichHeader("setTrace", EtlHeaders.TRACE_ID, message -> "custom-trace"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Header 'etlTraceId' is managed by EtlRoute and cannot be customized");

		assertThatThrownBy(() -> EtlRoute.builder("headerRoute")
			.enrichHeader("setNodeCount", EtlHeaders.NODE_COUNT, message -> 99))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Header 'etlNodeCount' is managed by EtlRoute and cannot be customized");

		assertThatThrownBy(() -> EtlRoute.builder("headerRoute")
			.enrichHeader("setSourceMessageId", EtlHeaders.SOURCE_MESSAGE_ID, message -> "custom-source"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Header 'etlSourceMessageId' is managed by EtlRoute and cannot be customized");
	}

	@Test
	void allowsCustomEnrichmentOfRouteSpecificHeaders() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeader("setEventType", EtlHeaders.EVENT_TYPE, message -> "custom-event")
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc").build());

		assertThat(output.getHeaders()).containsEntry(EtlHeaders.EVENT_TYPE, "custom-event");
	}

	@Test
	void allowsCustomEnrichmentOfMultipleRouteSpecificHeadersInOneNode() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeaders("setContractHeaders", Map.of(
				EtlHeaders.EVENT_TYPE, "custom-event",
				EtlHeaders.EVENT_VERSION, "v9"))
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc").build());

		assertThat(route.contractHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of(
			EtlHeaders.EVENT_TYPE, "custom-event",
			EtlHeaders.EVENT_VERSION, "v9"));
		assertThatThrownBy(() -> route.contractHeaders().put("mutate", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThat(output.getHeaders())
			.containsEntry(EtlHeaders.EVENT_TYPE, "custom-event")
			.containsEntry(EtlHeaders.EVENT_VERSION, "v9")
			.containsEntry(EtlHeaders.LAST_NODE, "setContractHeaders")
			.containsEntry(EtlHeaders.NODE_COUNT, 1);
	}

	@Test
	void allowsRepeatedStaticContractHeadersWhenValuesMatch() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeaders("setContractHeaders", Map.of(EtlHeaders.EVENT_TYPE, "custom-event"))
			.enrichHeaders("confirmContractHeaders", Map.of(EtlHeaders.EVENT_TYPE, "custom-event"))
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc").build());

		assertThat(route.contractHeaders()).containsExactly(Map.entry(EtlHeaders.EVENT_TYPE, "custom-event"));
		assertThat(output.getHeaders())
			.containsEntry(EtlHeaders.EVENT_TYPE, "custom-event")
			.containsEntry(EtlHeaders.LAST_NODE, "confirmContractHeaders")
			.containsEntry(EtlHeaders.NODE_COUNT, 2);
	}

	@Test
	void rejectsConflictingStaticContractHeaderDeclarations() {
		assertThatThrownBy(() -> EtlRoute.builder("headerRoute")
			.enrichHeaders("setContractHeaders", Map.of(EtlHeaders.EVENT_TYPE, "custom-event"))
			.enrichHeaders("conflictContractHeaders", Map.of(EtlHeaders.EVENT_TYPE, "other-event")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route 'headerRoute' already declares contract header 'etlEventType' as 'custom-event' "
				+ "and cannot redeclare it as 'other-event'");
	}

	@Test
	void dynamicMultiHeaderEnrichmentDoesNotAdvertiseStaticContractHeaders() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeaders("setDynamicHeaders", message -> Map.of(
				EtlHeaders.EVENT_TYPE, message.getPayload().toString()))
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("custom-event").build());

		assertThat(route.contractHeaders()).isEmpty();
		assertThat(output.getHeaders()).containsEntry(EtlHeaders.EVENT_TYPE, "custom-event");
	}

	@Test
	void requiredInputHeadersValidateBeforeDownstreamNodes() {
		EtlRoute route = EtlRoute.builder("inputContractRoute")
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "source-event",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("uppercase", payload -> ((String) payload).toUpperCase())
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.EVENT_TYPE, "source-event")
			.setHeader(EtlHeaders.EVENT_VERSION, "v1")
			.build());

		assertThat(output.getPayload()).isEqualTo("ABC");
		assertThat(route.inputContractHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of(
			EtlHeaders.EVENT_TYPE, "source-event",
			EtlHeaders.EVENT_VERSION, "v1"));
		assertThat(output.getHeaders()).containsEntry(EtlHeaders.NODE_COUNT, 2);
	}

	@Test
	void rejectsMessagesMissingRequiredInputHeadersWithRouteAndNodeContext() {
		EtlRoute route = EtlRoute.builder("inputContractRoute")
			.requireInputHeaders("requireContract", Map.of(EtlHeaders.EVENT_TYPE, "source-event"))
			.transformPayload("uppercase", payload -> ((String) payload).toUpperCase())
			.build();
		Message<?> input = MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-input-contract-1")
			.build();

		assertThatThrownBy(() -> route.execute(input))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("inputContractRoute");
				assertThat(exception.nodeName()).isEqualTo("requireContract");
				assertThat(exception.traceId()).isEqualTo("trace-input-contract-1");
				assertThat(exception.sourceMessageId()).isEqualTo(input.getHeaders().getId().toString());
				assertThat(exception.sourceMessageTimestamp()).isEqualTo(input.getHeaders().getTimestamp());
				assertThat(exception.completedNodeCount()).isZero();
			})
			.hasMessage("Route 'inputContractRoute' failed at node 'requireContract' "
				+ "for trace 'trace-input-contract-1': "
				+ "Input contract header 'etlEventType' expected 'source-event' but received 'null'");
	}

	@Test
	void rejectsConflictingRequiredInputHeaderDeclarations() {
		assertThatThrownBy(() -> EtlRoute.builder("inputContractRoute")
			.requireInputHeaders("requireContract", Map.of(EtlHeaders.EVENT_TYPE, "source-event"))
			.requireInputHeaders("requireOtherContract", Map.of(EtlHeaders.EVENT_TYPE, "other-event")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Route 'inputContractRoute' already requires input header 'etlEventType' as "
				+ "'source-event' and cannot redeclare it as 'other-event'");
	}

	@Test
	void rejectsOutputMissingAdvertisedContractHeaders() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeaders("setContractHeaders", Map.of(EtlHeaders.EVENT_TYPE, "custom-event"))
			.node("removeContractHeader", (message, context) -> MessageBuilder.fromMessage(message)
				.removeHeader(EtlHeaders.EVENT_TYPE)
				.build())
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-contract-1")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("headerRoute");
				assertThat(exception.nodeName()).isEqualTo("removeContractHeader");
				assertThat(exception.traceId()).isEqualTo("trace-contract-1");
				assertThat(exception.completedNodeCount()).isEqualTo(2);
			})
			.hasMessage("Route 'headerRoute' failed at node 'removeContractHeader' for trace 'trace-contract-1': "
				+ "Route output contract header 'etlEventType' expected 'custom-event' but produced 'null'");
	}

	@Test
	void rejectsOutputThatOverridesAdvertisedContractHeaders() {
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeaders("setContractHeaders", Map.of(EtlHeaders.EVENT_TYPE, "custom-event"))
			.enrichHeader("overrideContractHeader", EtlHeaders.EVENT_TYPE, message -> "other-event")
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-contract-2")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("headerRoute");
				assertThat(exception.nodeName()).isEqualTo("overrideContractHeader");
				assertThat(exception.traceId()).isEqualTo("trace-contract-2");
				assertThat(exception.completedNodeCount()).isEqualTo(2);
			})
			.hasMessage("Route 'headerRoute' failed at node 'overrideContractHeader' for trace 'trace-contract-2': "
				+ "Route output contract header 'etlEventType' expected 'custom-event' but produced 'other-event'");
	}

	@Test
	void wrapsInvalidMultiHeaderEnrichmentWithRouteAndNodeContext() {
		Map<String, Object> headers = new LinkedHashMap<>();
		headers.put(EtlHeaders.EVENT_TYPE, "custom-event");
		headers.put(EtlHeaders.EVENT_VERSION, null);
		EtlRoute route = EtlRoute.builder("headerRoute")
			.enrichHeaders("setContractHeaders", message -> headers)
			.build();

		assertThatThrownBy(() -> route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-headers-1")
			.build()))
			.isInstanceOfSatisfying(EtlRouteExecutionException.class, exception -> {
				assertThat(exception.routeName()).isEqualTo("headerRoute");
				assertThat(exception.nodeName()).isEqualTo("setContractHeaders");
				assertThat(exception.traceId()).isEqualTo("trace-headers-1");
			})
			.hasMessage("Route 'headerRoute' failed at node 'setContractHeaders' for trace 'trace-headers-1': "
				+ "Header value for 'etlEventVersion' must not be null");
	}

	@Test
	void preservesExistingTraceId() {
		EtlRoute route = EtlRoute.builder("traceRoute")
			.transformPayload("noop", payload -> payload)
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, "trace-123")
			.build());

		assertThat(output.getHeaders()).containsEntry(EtlHeaders.TRACE_ID, "trace-123");
	}

	@Test
	void trimsExistingTraceId() {
		EtlRoute route = EtlRoute.builder("traceRoute")
			.transformPayload("noop", payload -> payload)
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, " trace-123 ")
			.build());

		assertThat(output.getHeaders()).containsEntry(EtlHeaders.TRACE_ID, "trace-123");
	}

	@Test
	void generatesTraceIdWhenExistingTraceIdIsBlank() {
		EtlRoute route = EtlRoute.builder("traceRoute")
			.transformPayload("noop", payload -> payload)
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc")
			.setHeader(EtlHeaders.TRACE_ID, " ")
			.build());

		assertThat((String) output.getHeaders().get(EtlHeaders.TRACE_ID)).isNotBlank();
		assertThat(output.getHeaders().get(EtlHeaders.TRACE_ID)).isNotEqualTo(" ");
	}

	@Test
	void exposesImmutableNodeDurationHeader() {
		EtlRoute route = EtlRoute.builder("timedRoute")
			.transformPayload("first", payload -> payload)
			.transformPayload("second", payload -> payload)
			.build();

		Message<?> output = route.execute(MessageBuilder.withPayload("abc").build());
		Map<String, Long> durations = nodeDurations(output);

		assertThat(durations).containsOnlyKeys("first", "second");
		assertThatThrownBy(() -> durations.put("mutate", 1L))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Long> nodeDurations(Message<?> message) {
		return (Map<String, Long>) message.getHeaders().get(EtlHeaders.NODE_DURATIONS_NANOS);
	}
}
