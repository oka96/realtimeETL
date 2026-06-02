package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import com.example.realtimeetl.etl.EtlFailureEvent;
import com.example.realtimeetl.etl.EtlHeaders;
import com.example.realtimeetl.invoice.InvoiceEvent;
import com.example.realtimeetl.order.OrderCreated;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.core.io.ClassPathResource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

@EnableTestBinder
@SpringBootTest
class OrderInvoiceRouteIntegrationTest {

	@Autowired
	private InputDestination input;

	@Autowired
	private OutputDestination output;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void transformsExampleOrderIntoInvoiceEvent() throws Exception {
		OrderCreated order = readJson("examples/order-invoice-input.json", OrderCreated.class);
		InvoiceEvent expected = readJson("examples/order-invoice-output.json", InvoiceEvent.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.setHeader(EtlHeaders.TRACE_ID, "invoice-trace-1001")
			.build(), "orders.invoice.input");

		Message<byte[]> result = output.receive(1_000, "invoices.output");

		assertThat(result).isNotNull();
		InvoiceEvent invoice = objectMapper.readValue(result.getPayload(), InvoiceEvent.class);
		assertThat(invoice).isEqualTo(expected);
		assertThat(result.getHeaders())
			.containsEntry(EtlHeaders.ROUTE, RouteNames.ORDER_INVOICE)
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, OrderCreated.class.getName())
			.containsEntry(EtlHeaders.OUTPUT_PAYLOAD_TYPE, InvoiceEvent.class.getName())
			.containsEntry(EtlHeaders.LAST_NODE, "markInvoiceRoute")
			.containsEntry(EtlHeaders.NODE_COUNT, 5)
			.containsEntry(EtlHeaders.TRACE_ID, "invoice-trace-1001")
			.containsEntry(EtlHeaders.EVENT_TYPE, RouteEventTypes.INVOICE)
			.containsEntry(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1);
		assertThat((Long) result.getHeaders().get(EtlHeaders.DURATION_NANOS)).isPositive();
		assertThat((String) result.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_ID)).isNotBlank();
		assertThat((Long) result.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP)).isPositive();
		assertThat(nodeDurations(result))
			.containsOnlyKeys(
				"requireOrderContract",
				"validateOrder",
				"normalizeOrder",
				"buildInvoice",
				"markInvoiceRoute");
	}

	@Test
	void preservesInvoiceAmountScaleForDecimalOrders() throws Exception {
		OrderCreated order = new OrderCreated(
			"O-2002",
			"C-10",
			new BigDecimal("19.99"),
			"MYR");

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.invoice.input");

		Message<byte[]> result = output.receive(1_000, "invoices.output");

		assertThat(result).isNotNull();
		InvoiceEvent invoice = objectMapper.readValue(result.getPayload(), InvoiceEvent.class);
		assertThat(invoice.amount()).isEqualByComparingTo("19.99");
		assertThat(invoice.currency()).isEqualTo("MYR");
	}

	@Test
	void normalizesWhitespaceAndCurrencyBeforeBuildingInvoice() throws Exception {
		OrderCreated order = readJson("examples/order-invoice-normalized-input.json", OrderCreated.class);
		InvoiceEvent expected = readJson("examples/order-invoice-normalized-output.json", InvoiceEvent.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.invoice.input");

		Message<byte[]> result = output.receive(1_000, "invoices.output");

		assertThat(result).isNotNull();
		InvoiceEvent invoice = objectMapper.readValue(result.getPayload(), InvoiceEvent.class);
		assertThat(invoice).isEqualTo(expected);
	}

	@Test
	void rejectsMissingInputContractWithoutPublishingInvoice() throws Exception {
		OrderCreated order = readJson("examples/order-invoice-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.build(), "orders.invoice.input");

		assertThat(output.receive(100, "invoices.output")).isNull();
	}

	@Test
	void rejectsInvalidOrdersWithoutPublishingInvoice() throws Exception {
		OrderCreated order = readJson("examples/order-invoice-invalid-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.setHeader(EtlHeaders.TRACE_ID, "invoice-invalid-trace-1")
			.build(), "orders.invoice.input");

		assertThat(output.receive(100, "invoices.output")).isNull();
		Message<byte[]> failureMessage = receiveFailureMessageForTrace("invoice-invalid-trace-1");
		assertThat(failureMessage.getHeaders())
			.containsEntry(EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE)
			.containsEntry(EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)
			.containsEntry(EtlHeaders.ROUTE, RouteNames.ORDER_INVOICE)
			.containsEntry(EtlHeaders.LAST_NODE, "validateOrder")
			.containsEntry(EtlHeaders.NODE_COUNT, 1)
			.containsEntry(EtlHeaders.TRACE_ID, "invoice-invalid-trace-1")
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, OrderCreated.class.getName());
		assertThat((Long) failureMessage.getHeaders().get(EtlHeaders.FAILURE_TIMESTAMP)).isPositive();
		assertThat((String) failureMessage.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_ID)).isNotBlank();
		assertThat((Long) failureMessage.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP)).isPositive();
		EtlFailureEvent failure = objectMapper.readValue(failureMessage.getPayload(), EtlFailureEvent.class);
		assertThat(failure.routeName()).isEqualTo(RouteNames.ORDER_INVOICE);
		assertThat(failure.nodeName()).isEqualTo("validateOrder");
		assertThat(failure.traceId()).isEqualTo("invoice-invalid-trace-1");
		assertThat(failure.failureTimestamp()).isPositive();
		assertThat(failure.inputPayloadType()).isEqualTo(OrderCreated.class.getName());
		assertThat(failure.sourceMessageId()).isNotBlank();
		assertThat(failure.sourceMessageTimestamp()).isPositive();
		assertThat(failure.completedNodeCount()).isEqualTo(1);
		assertThat(failureMessage.getHeaders())
			.containsEntry(EtlHeaders.FAILURE_TIMESTAMP, failure.failureTimestamp())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_ID, failure.sourceMessageId())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, failure.sourceMessageTimestamp());
		assertThat(failure.nodeDurationsNanos()).containsOnlyKeys("requireOrderContract", "validateOrder");
		assertThat(failure.errorType()).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(failure.errorMessage()).isEqualTo("Order id is required");
	}

	@Test
	void rejectsMalformedCurrencyWithoutPublishingInvoice() throws Exception {
		OrderCreated order = readJson("examples/order-invoice-invalid-currency-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.invoice.input");

		assertThat(output.receive(100, "invoices.output")).isNull();
	}

	private <T> T readJson(String path, Class<T> type) throws Exception {
		return objectMapper.readValue(new ClassPathResource(path).getInputStream(), type);
	}

	private Message<byte[]> receiveFailureMessageForTrace(String traceId) throws Exception {
		for (int attempt = 0; attempt < 10; attempt++) {
			Message<byte[]> failureMessage = output.receive(1_000, "etl.failures");
			if (failureMessage == null) {
				continue;
			}
			EtlFailureEvent failure = objectMapper.readValue(failureMessage.getPayload(), EtlFailureEvent.class);
			if (traceId.equals(failure.traceId())) {
				return failureMessage;
			}
		}
		throw new AssertionError("No ETL failure event received for trace " + traceId);
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Long> nodeDurations(Message<?> message) {
		return (Map<String, Long>) message.getHeaders().get(EtlHeaders.NODE_DURATIONS_NANOS);
	}
}
