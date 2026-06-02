package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EtlRouteBindingValidatorTest {

	@Test
	void rejectsNullRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteBindingValidator(null, environment()))
			.withMessage("registry must not be null");
	}

	@Test
	void rejectsNullEnvironment() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteBindingValidator(new EtlRouteRegistry(List.of(route("orderInvoice"))), null))
			.withMessage("environment must not be null");
	}

	@Test
	void acceptsRoutesWithFunctionDefinitionsAndStreamBindings() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"), route("fraudReview"))),
			environment()
				.withProperty("spring.cloud.function.definition", "orderInvoice; fraudReview")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.destination", "orders.invoice.input")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type", "application/json")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.group", "realtime-etl-order-invoice")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", "3")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.back-off-initial-interval", "500")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.back-off-max-interval", "5000")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.destination", "invoices.output")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.content-type", "application/json")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.destination", "orders.fraud.input")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.content-type", "application/json")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.group", "realtime-etl-fraud-review")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.consumer.max-attempts", "3")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.consumer.back-off-initial-interval", "500")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.consumer.back-off-max-interval", "5000")
				.withProperty("spring.cloud.stream.bindings.fraudReview-out-0.destination", "fraud-reviews.output")
				.withProperty("spring.cloud.stream.bindings.fraudReview-out-0.content-type", "application/json")
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "etl.failures")
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", "application/json"));

		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	void rejectsRouteMissingFromFunctionDefinition() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.function.definition", ""));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' is missing from spring.cloud.function.definition");
	}

	@Test
	void rejectsFunctionDefinitionWithoutRegisteredRoute() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.function.definition", "orderInvoice;staleRoute"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Spring function 'staleRoute' has no registered ETL route");
	}

	@Test
	void rejectsDuplicateFunctionDefinitions() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.function.definition", "orderInvoice;orderInvoice"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("spring.cloud.function.definition contains duplicate function 'orderInvoice'");
	}

	@Test
	void rejectsBlankFunctionDefinitionEntries() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"), route("fraudReview"))),
			validTwoRouteEnvironment()
				.withProperty("spring.cloud.function.definition", "orderInvoice;;fraudReview"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("spring.cloud.function.definition must not contain blank function entries");
	}

	@Test
	void rejectsTrailingBlankFunctionDefinitionEntry() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.function.definition", "orderInvoice;"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("spring.cloud.function.definition must not contain blank function entries");
	}

	@Test
	void rejectsFunctionDefinitionsInDifferentOrderThanRouteCatalog() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"), route("fraudReview"))),
			validTwoRouteEnvironment()
				.withProperty("spring.cloud.function.definition", "fraudReview;orderInvoice"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("spring.cloud.function.definition order [fraudReview, orderInvoice] must match "
				+ "registered ETL route order [orderInvoice, fraudReview]");
	}

	@Test
	void rejectsMissingBindingDestination() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			environment()
				.withProperty("spring.cloud.function.definition", "orderInvoice"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' is missing stream binding destination "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.destination");
	}

	@Test
	void rejectsRoutesWithoutConcreteInputPayloadType() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(Object.class, String.class)
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare a concrete input payload type");
	}

	@Test
	void rejectsRoutesWithoutConcreteOutputPayloadType() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, Object.class)
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare a concrete output payload type");
	}

	@Test
	void rejectsRoutesWithoutOutputContractEventType() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare non-blank output contract header etlEventType");
	}

	@Test
	void rejectsRoutesWithoutOutputContractEventVersion() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(EtlHeaders.EVENT_TYPE, "order-invoice"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare non-blank output contract header etlEventVersion");
	}

	@Test
	void rejectsBlankOutputContractHeaders() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, " "))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare non-blank output contract header etlEventVersion");
	}

	@Test
	void rejectsNonStringOutputContractHeaders() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, 1))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare non-blank output contract header etlEventVersion");
	}

	@Test
	void rejectsOutputContractEventTypeThatIsNotLowercaseKebabCase() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "OrderInvoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' output contract header etlEventType "
				+ "must use lowercase kebab-case");
	}

	@Test
	void rejectsOutputContractEventVersionThatDoesNotMatchVersionToken() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "version-one"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' output contract header etlEventVersion must match v<digits>");
	}

	@Test
	void rejectsOutputContractHeaderWithSurroundingWhitespace() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, " orderInvoice ",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' output contract header etlEventType "
				+ "must not contain surrounding whitespace");
	}

	@Test
	void rejectsRoutesWithoutInputContractEventType() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare non-blank input contract header etlEventType");
	}

	@Test
	void rejectsRoutesWithoutInputContractEventVersion() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(EtlHeaders.EVENT_TYPE, "order-created"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' must declare non-blank input contract header etlEventVersion");
	}

	@Test
	void rejectsInputContractEventTypeThatIsNotLowercaseKebabCase() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "OrderCreated",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input contract header etlEventType "
				+ "must use lowercase kebab-case");
	}

	@Test
	void rejectsInputContractEventVersionThatDoesNotMatchVersionToken() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input contract header etlEventVersion must match v<digits>");
	}

	@Test
	void rejectsInputContractHeaderWithSurroundingWhitespace() {
		EtlRoute route = EtlRoute.builder("orderInvoice")
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, " order-created ",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-invoice",
				EtlHeaders.EVENT_VERSION, "v1"))
			.transformPayload("noop", payload -> payload)
			.build();
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route)),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input contract header etlEventType "
				+ "must not contain surrounding whitespace");
	}

	@Test
	void rejectsMissingInputConsumerGroup() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			environment()
				.withProperty("spring.cloud.function.definition", "orderInvoice")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.destination", "orders.invoice.input")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type", "application/json")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.destination", "invoices.output")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.content-type", "application/json"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input binding must configure a durable consumer group at "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.group");
	}

	@Test
	void rejectsInputConsumerGroupWithSurroundingWhitespace() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.group", " realtime-etl-order-invoice "));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' binding property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.group must not contain surrounding whitespace");
	}

	@Test
	void rejectsBindingDestinationWithSurroundingWhitespace() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.destination", " orders.invoice.input "));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' binding property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.destination must not contain surrounding whitespace");
	}

	@Test
	void rejectsRouteInputAndOutputUsingSameDestination() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.destination", "orders.invoice.input"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input and output bindings must use different destinations "
				+ "but both use 'orders.invoice.input'");
	}

	@Test
	void rejectsDuplicateOutputDestinationsAcrossRoutes() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"), route("fraudReview"))),
			validTwoRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.fraudReview-out-0.destination", "invoices.output"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'fraudReview' output destination 'invoices.output' is already used by route "
				+ "'orderInvoice'");
	}

	@Test
	void rejectsDuplicateOutputContractsAcrossRoutes() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(
				route("orderInvoice", "order-processed", "v1"),
				route("fraudReview", "order-processed", "v1"))),
			validTwoRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'fraudReview' output contract etlEventType='order-processed' "
				+ "etlEventVersion='v1' is already used by route 'orderInvoice'");
	}

	@Test
	void rejectsRouteOutputContractReservedForFailureEvents() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(
				route("orderInvoice", EtlFailureEvent.EVENT_TYPE, EtlFailureEvent.EVENT_VERSION))),
			validSingleRouteEnvironment());

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' output contract etlEventType='etl-route-failure' "
				+ "etlEventVersion='v1' is reserved for ETL failure events");
	}

	@Test
	void acceptsSharedInputDestinationWhenConsumerGroupsDiffer() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"), route("fraudReview"))),
			validTwoRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.destination", "orders.invoice.input"));

		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	void rejectsDuplicateInputDestinationAndConsumerGroupAcrossRoutes() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"), route("fraudReview"))),
			validTwoRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.destination", "orders.invoice.input")
				.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.group", "realtime-etl-order-invoice"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'fraudReview' input destination 'orders.invoice.input' and group "
				+ "'realtime-etl-order-invoice' are already used by route 'orderInvoice'");
	}

	@Test
	void rejectsNonJsonContentType() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			environment()
				.withProperty("spring.cloud.function.definition", "orderInvoice")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.destination", "orders.invoice.input")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type", "text/plain")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.group", "realtime-etl-order-invoice"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' binding orderInvoice-in-0 must use application/json content-type at "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.content-type");
	}

	@Test
	void acceptsJsonContentTypeWithCharsetParameter() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type",
					"application/json;charset=UTF-8")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.content-type",
					"application/json;charset=UTF-8")
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type",
					"application/json;charset=UTF-8"));

		assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMalformedContentType() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type", "application"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' binding orderInvoice-in-0 must use application/json content-type at "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.content-type");
	}

	@Test
	void rejectsContentTypeWithSurroundingWhitespace() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type", " application/json "));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' binding property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.content-type "
				+ "must not contain surrounding whitespace");
	}

	@Test
	void rejectsMissingRetryProperties() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", ""));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input binding must configure positive retry property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts");
	}

	@Test
	void rejectsNonIntegerRetryProperties() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", "three"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input binding retry property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts must be an integer");
	}

	@Test
	void rejectsRetryPropertyWithSurroundingWhitespace() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", " 3 "));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' binding property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts "
				+ "must not contain surrounding whitespace");
	}

	@Test
	void rejectsNonPositiveRetryProperties() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", "0"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input binding retry property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts must be positive");
	}

	@Test
	void rejectsMaxAttemptsThatDoNotEnableRetries() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", "1"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input binding retry property "
				+ "spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts "
				+ "must be at least 2 to enable retries");
	}

	@Test
	void rejectsMaxBackoffLowerThanInitialBackoff() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.back-off-initial-interval", "500")
				.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.back-off-max-interval", "100"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'orderInvoice' input binding retry back-off-max-interval must be "
				+ "greater than or equal to back-off-initial-interval");
	}

	@Test
	void rejectsMissingFailureEventDestination() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", ""));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'etlFailures' is missing stream binding destination "
				+ "spring.cloud.stream.bindings.etlFailures-out-0.destination");
	}

	@Test
	void rejectsFailureEventBindingWithNonJsonContentType() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", "text/plain"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'etlFailures' binding etlFailures-out-0 must use application/json content-type at "
				+ "spring.cloud.stream.bindings.etlFailures-out-0.content-type");
	}

	@Test
	void rejectsFailureEventBindingContentTypeWithSurroundingWhitespace() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", " application/json "));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'etlFailures' binding property "
				+ "spring.cloud.stream.bindings.etlFailures-out-0.content-type "
				+ "must not contain surrounding whitespace");
	}

	@Test
	void rejectsFailureEventDestinationSharedWithRouteOutput() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "invoices.output"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL route 'etlFailures' output destination 'invoices.output' is already used by route "
				+ "'orderInvoice'");
	}

	@Test
	void rejectsFailureEventDestinationSharedWithRouteInput() {
		EtlRouteBindingValidator validator = new EtlRouteBindingValidator(
			new EtlRouteRegistry(List.of(route("orderInvoice"))),
			validSingleRouteEnvironment()
				.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "orders.invoice.input"));

		assertThatThrownBy(() -> validator.run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("ETL failure event destination 'orders.invoice.input' must not match input destination "
				+ "used by route 'orderInvoice'");
	}

	private static MockEnvironment environment() {
		return new MockEnvironment();
	}

	private static MockEnvironment validSingleRouteEnvironment() {
		return environment()
			.withProperty("spring.cloud.function.definition", "orderInvoice")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.destination", "orders.invoice.input")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.group", "realtime-etl-order-invoice")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.max-attempts", "3")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.back-off-initial-interval", "500")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-in-0.consumer.back-off-max-interval", "5000")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.destination", "invoices.output")
			.withProperty("spring.cloud.stream.bindings.orderInvoice-out-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.destination", "etl.failures")
			.withProperty("spring.cloud.stream.bindings.etlFailures-out-0.content-type", "application/json");
	}

	private static MockEnvironment validTwoRouteEnvironment() {
		return validSingleRouteEnvironment()
			.withProperty("spring.cloud.function.definition", "orderInvoice;fraudReview")
			.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.destination", "orders.fraud.input")
			.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.content-type", "application/json")
			.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.group", "realtime-etl-fraud-review")
			.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.consumer.max-attempts", "3")
			.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.consumer.back-off-initial-interval", "500")
			.withProperty("spring.cloud.stream.bindings.fraudReview-in-0.consumer.back-off-max-interval", "5000")
			.withProperty("spring.cloud.stream.bindings.fraudReview-out-0.destination", "fraud-reviews.output")
			.withProperty("spring.cloud.stream.bindings.fraudReview-out-0.content-type", "application/json");
	}

	private static EtlRoute route(String name) {
		return route(name, eventTypeFor(name), "v1");
	}

	private static EtlRoute route(String name, String outputEventType, String outputEventVersion) {
		return EtlRoute.builder(name)
			.payloadTypes(String.class, String.class)
			.requireInputHeaders("requireContract", Map.of(
				EtlHeaders.EVENT_TYPE, "order-created",
				EtlHeaders.EVENT_VERSION, "v1"))
			.enrichHeaders("markContract", Map.of(
				EtlHeaders.EVENT_TYPE, outputEventType,
				EtlHeaders.EVENT_VERSION, outputEventVersion))
			.transformPayload("noop", payload -> payload)
			.build();
	}

	private static String eventTypeFor(String routeName) {
		return routeName.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
	}
}
