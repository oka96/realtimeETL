package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.example.realtimeetl.etl.EtlFailureEvent;
import com.example.realtimeetl.etl.EtlHeaders;
import com.example.realtimeetl.fraud.FraudReviewEvent;
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
class FraudReviewRouteIntegrationTest {

	@Autowired
	private InputDestination input;

	@Autowired
	private OutputDestination output;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void transformsExampleHighRiskOrderIntoFraudReviewEvent() throws Exception {
		OrderCreated order = readJson("examples/fraud-review-input.json", OrderCreated.class);
		FraudReviewEvent expected = readJson("examples/fraud-review-output.json", FraudReviewEvent.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.fraud.input");

		Message<byte[]> result = output.receive(1_000, "fraud-reviews.output");

		assertThat(result).isNotNull();
		FraudReviewEvent review = objectMapper.readValue(result.getPayload(), FraudReviewEvent.class);
		assertThat(review).isEqualTo(expected);
		assertThat(result.getHeaders())
			.containsEntry(EtlHeaders.ROUTE, RouteNames.FRAUD_REVIEW)
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, OrderCreated.class.getName())
			.containsEntry(EtlHeaders.OUTPUT_PAYLOAD_TYPE, FraudReviewEvent.class.getName())
			.containsEntry(EtlHeaders.LAST_NODE, "markFraudRoute")
			.containsEntry(EtlHeaders.NODE_COUNT, 6)
			.containsEntry(EtlHeaders.EVENT_TYPE, RouteEventTypes.FRAUD_REVIEW)
			.containsEntry(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1);
		assertThat((String) result.getHeaders().get(EtlHeaders.TRACE_ID)).isNotBlank();
		assertThat((Long) result.getHeaders().get(EtlHeaders.DURATION_NANOS)).isPositive();
		assertThat((String) result.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_ID)).isNotBlank();
		assertThat((Long) result.getHeaders().get(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP)).isPositive();
		assertThat(nodeDurations(result))
			.containsOnlyKeys(
				"requireOrderContract",
				"validateOrder",
				"normalizeOrder",
				"scoreRisk",
				"buildReview",
				"markFraudRoute");
	}

	@Test
	void autoApprovesLowRiskOrders() throws Exception {
		OrderCreated order = readJson("examples/fraud-review-normalized-input.json", OrderCreated.class);
		FraudReviewEvent expected = readJson("examples/fraud-review-normalized-output.json", FraudReviewEvent.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.fraud.input");

		Message<byte[]> result = output.receive(1_000, "fraud-reviews.output");

		assertThat(result).isNotNull();
		FraudReviewEvent review = objectMapper.readValue(result.getPayload(), FraudReviewEvent.class);
		assertThat(review).isEqualTo(expected);
	}

	@Test
	void rejectsInvalidOrdersWithoutPublishingFraudReview() throws Exception {
		OrderCreated order = readJson("examples/fraud-review-invalid-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.setHeader(EtlHeaders.TRACE_ID, "fraud-invalid-trace-1")
			.build(), "orders.fraud.input");

		assertThat(output.receive(100, "fraud-reviews.output")).isNull();
		Message<byte[]> failureMessage = receiveFailureMessageForTrace("fraud-invalid-trace-1");
		assertThat(failureMessage.getHeaders())
			.containsEntry(EtlHeaders.EVENT_TYPE, EtlFailureEvent.EVENT_TYPE)
			.containsEntry(EtlHeaders.EVENT_VERSION, EtlFailureEvent.EVENT_VERSION)
			.containsEntry(EtlHeaders.ROUTE, RouteNames.FRAUD_REVIEW)
			.containsEntry(EtlHeaders.LAST_NODE, "validateOrder")
			.containsEntry(EtlHeaders.NODE_COUNT, 1)
			.containsEntry(EtlHeaders.TRACE_ID, "fraud-invalid-trace-1")
			.containsEntry(EtlHeaders.INPUT_PAYLOAD_TYPE, OrderCreated.class.getName());
		assertThat((Long) failureMessage.getHeaders().get(EtlHeaders.FAILURE_TIMESTAMP)).isPositive();
		EtlFailureEvent failure = objectMapper.readValue(failureMessage.getPayload(), EtlFailureEvent.class);
		assertThat(failure.routeName()).isEqualTo(RouteNames.FRAUD_REVIEW);
		assertThat(failure.nodeName()).isEqualTo("validateOrder");
		assertThat(failure.traceId()).isEqualTo("fraud-invalid-trace-1");
		assertThat(failure.failureTimestamp()).isPositive();
		assertThat(failure.inputPayloadType()).isEqualTo(OrderCreated.class.getName());
		assertThat(failure.completedNodeCount()).isEqualTo(1);
		assertThat(failureMessage.getHeaders())
			.containsEntry(EtlHeaders.FAILURE_TIMESTAMP, failure.failureTimestamp())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_ID, failure.sourceMessageId())
			.containsEntry(EtlHeaders.SOURCE_MESSAGE_TIMESTAMP, failure.sourceMessageTimestamp());
		assertThat(failure.nodeDurationsNanos()).containsOnlyKeys("requireOrderContract", "validateOrder");
		assertThat(failure.errorType()).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(failure.errorMessage()).isEqualTo("Order amount must be positive");
	}

	@Test
	void rejectsMalformedCurrencyWithoutPublishingFraudReview() throws Exception {
		OrderCreated order = readJson("examples/fraud-review-invalid-currency-input.json", OrderCreated.class);

		input.send(MessageBuilder.withPayload(order)
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.fraud.input");

		assertThat(output.receive(100, "fraud-reviews.output")).isNull();
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
