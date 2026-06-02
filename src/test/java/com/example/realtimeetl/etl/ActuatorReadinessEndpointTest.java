package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.example.realtimeetl.order.OrderCreated;
import com.example.realtimeetl.routes.RouteEventTypes;
import com.example.realtimeetl.routes.RouteEventVersions;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeTypeUtils;

@EnableTestBinder
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorReadinessEndpointTest {

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private InputDestination input;

	@Autowired
	private OutputDestination output;

	@Test
	void readinessEndpointExposesEtlRoutesHealth() {
		ResponseEntity<JsonNode> response = restTemplate.getForEntity("/actuator/health/readiness", JsonNode.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.path("status").asText()).isEqualTo("UP");
		JsonNode etlRoutes = body.path("components").path("etlRoutes");
		assertThat(etlRoutes.path("status").asText()).isEqualTo("UP");
		assertThat(etlRoutes.path("details").path("routeCount").asInt()).isEqualTo(3);
		assertThat(etlRoutes.path("details").path("failureBinding").path("name").asText())
			.isEqualTo(EtlFailureEventPublisher.BINDING_NAME);
		assertThat(etlRoutes.path("details").path("failureBinding").path("destination").asText())
			.isEqualTo("etl.failures");
		assertThat(etlRoutes.path("details").path("failureBinding").path("payloadType").asText())
			.isEqualTo(EtlFailureEvent.class.getName());
		assertThat(etlRoutes.path("details").path("failureBinding").path("contractHeaders").path(EtlHeaders.EVENT_TYPE).asText())
			.isEqualTo(EtlFailureEvent.EVENT_TYPE);
		assertThat(etlRoutes.path("details").path("failureBinding").path("contractHeaders").path(EtlHeaders.EVENT_VERSION).asText())
			.isEqualTo(EtlFailureEvent.EVENT_VERSION);
		assertThat(etlRoutes.path("details").path("routes"))
			.anySatisfy(route -> assertThat(route.path("routeName").asText()).isEqualTo("orderInvoice"))
			.anySatisfy(route -> assertThat(route.path("routeName").asText()).isEqualTo("fraudReview"))
			.anySatisfy(route -> assertThat(route.path("routeName").asText()).isEqualTo("orderAnalytics"));
	}

	@Test
	void infoEndpointExposesEtlRouteCatalog() {
		ResponseEntity<JsonNode> response = restTemplate.getForEntity("/actuator/info", JsonNode.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		JsonNode etl = body.path("etl");
		assertThat(etl.path("routeCount").asInt()).isEqualTo(3);
		assertThat(etl.path("failureBinding").path("name").asText())
			.isEqualTo(EtlFailureEventPublisher.BINDING_NAME);
		assertThat(etl.path("failureBinding").path("destination").asText()).isEqualTo("etl.failures");
		assertThat(etl.path("failureBinding").path("payloadType").asText())
			.isEqualTo(EtlFailureEvent.class.getName());
		assertThat(etl.path("failureBinding").path("contractHeaders").path(EtlHeaders.EVENT_TYPE).asText())
			.isEqualTo(EtlFailureEvent.EVENT_TYPE);
		assertThat(etl.path("failureBinding").path("contractHeaders").path(EtlHeaders.EVENT_VERSION).asText())
			.isEqualTo(EtlFailureEvent.EVENT_VERSION);
		assertThat(etl.path("routes"))
			.anySatisfy(route -> {
				assertThat(route.path("routeName").asText()).isEqualTo("orderInvoice");
				assertThat(route.path("inputBinding").path("name").asText()).isEqualTo("orderInvoice-in-0");
				assertThat(route.path("inputBinding").path("destination").asText()).isEqualTo("orders.invoice.input");
				assertThat(route.path("inputBinding").path("group").asText())
					.isEqualTo("realtime-etl-order-invoice");
				assertThat(route.path("inputBinding").path("retry").path("maxAttempts").asInt()).isEqualTo(3);
				assertThat(route.path("inputBinding").path("retry").path("backOffInitialInterval").asInt()).isEqualTo(500);
				assertThat(route.path("inputBinding").path("retry").path("backOffMaxInterval").asInt()).isEqualTo(5000);
				assertThat(route.path("outputBinding").path("name").asText()).isEqualTo("orderInvoice-out-0");
				assertThat(route.path("outputBinding").path("destination").asText()).isEqualTo("invoices.output");
				assertThat(route.path("inputContractHeaders").path(EtlHeaders.EVENT_TYPE).asText())
					.isEqualTo(RouteEventTypes.ORDER_CREATED);
				assertThat(route.path("inputContractHeaders").path(EtlHeaders.EVENT_VERSION).asText())
					.isEqualTo(RouteEventVersions.V1);
				assertThat(route.path("contractHeaders").path(EtlHeaders.EVENT_TYPE).asText())
					.isEqualTo(RouteEventTypes.INVOICE);
				assertThat(route.path("contractHeaders").path(EtlHeaders.EVENT_VERSION).asText())
					.isEqualTo(RouteEventVersions.V1);
				assertThat(route.path("nodeCount").asInt()).isEqualTo(5);
			})
			.anySatisfy(route -> assertThat(route.path("routeName").asText()).isEqualTo("fraudReview"))
			.anySatisfy(route -> assertThat(route.path("routeName").asText()).isEqualTo("orderAnalytics"));
	}

	@Test
	void metricsEndpointExposesRouteMessageCounterAfterProcessing() {
		input.send(MessageBuilder.withPayload(new OrderCreated(
			"O-METRIC-1",
			"C-METRIC-1",
			new BigDecimal("42.00"),
			"MYR"))
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.invoice.input");
		Message<byte[]> invoice = output.receive(1_000, "invoices.output");
		assertThat(invoice).isNotNull();

		ResponseEntity<JsonNode> response = restTemplate.getForEntity(
			"/actuator/metrics/realtime.etl.route.messages?tag=route:orderInvoice&tag=result:success",
			JsonNode.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.path("name").asText()).isEqualTo(EtlRouteMetrics.MESSAGE_COUNTER);
		assertThat(body.path("measurements"))
			.anySatisfy(measurement -> {
				assertThat(measurement.path("statistic").asText()).isEqualTo("COUNT");
				assertThat(measurement.path("value").asDouble()).isGreaterThanOrEqualTo(1.0);
			});
	}

	@Test
	void metricsEndpointExposesRouteCatalogGauges() {
		ResponseEntity<JsonNode> routeCountResponse = restTemplate.getForEntity(
			"/actuator/metrics/realtime.etl.routes.configured",
			JsonNode.class);
		ResponseEntity<JsonNode> nodeCountResponse = restTemplate.getForEntity(
			"/actuator/metrics/realtime.etl.route.nodes?tag=route:orderInvoice",
			JsonNode.class);

		assertThat(routeCountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode routeCountBody = routeCountResponse.getBody();
		assertThat(routeCountBody).isNotNull();
		assertThat(routeCountBody.path("name").asText()).isEqualTo(EtlRouteCatalogMetrics.ROUTE_COUNT_GAUGE);
		assertThat(routeCountBody.path("measurements"))
			.anySatisfy(measurement -> {
				assertThat(measurement.path("statistic").asText()).isEqualTo("VALUE");
				assertThat(measurement.path("value").asDouble()).isEqualTo(3.0);
			});

		assertThat(nodeCountResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode nodeCountBody = nodeCountResponse.getBody();
		assertThat(nodeCountBody).isNotNull();
		assertThat(nodeCountBody.path("name").asText()).isEqualTo(EtlRouteCatalogMetrics.ROUTE_NODE_COUNT_GAUGE);
		assertThat(nodeCountBody.path("measurements"))
			.anySatisfy(measurement -> {
				assertThat(measurement.path("statistic").asText()).isEqualTo("VALUE");
				assertThat(measurement.path("value").asDouble()).isEqualTo(5.0);
			});
	}

	@Test
	void prometheusEndpointExposesRouteMessageCounterAfterProcessing() {
		input.send(MessageBuilder.withPayload(new OrderCreated(
			"O-PROM-1",
			"C-PROM-1",
			new BigDecimal("84.00"),
			"MYR"))
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.invoice.input");
		Message<byte[]> invoice = output.receive(1_000, "invoices.output");
		assertThat(invoice).isNotNull();

		ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody())
			.contains("realtime_etl_route_messages_total")
			.contains("route=\"orderInvoice\"")
			.contains("result=\"success\"");
	}

	@Test
	void metricsAndPrometheusEndpointsExposeFailureCountersAfterRejectedMessage() {
		input.send(MessageBuilder.withPayload(new OrderCreated(
			"O-METRIC-FAIL-1",
			"C-METRIC-FAIL-1",
			BigDecimal.ZERO,
			"MYR"))
			.setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
			.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
			.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
			.build(), "orders.invoice.input");
		assertThat(output.receive(100, "invoices.output")).isNull();
		assertThat(output.receive(1_000, "etl.failures")).isNotNull();

		ResponseEntity<JsonNode> routeFailureMetricsResponse = restTemplate.getForEntity(
			"/actuator/metrics/realtime.etl.route.messages?tag=route:orderInvoice&tag=result:failure",
			JsonNode.class);
		ResponseEntity<JsonNode> failureEventMetricsResponse = restTemplate.getForEntity(
			"/actuator/metrics/realtime.etl.failure.events"
				+ "?tag=route:orderInvoice&tag=node:validateOrder&tag=result:accepted",
			JsonNode.class);
		ResponseEntity<String> prometheusResponse = restTemplate.getForEntity("/actuator/prometheus", String.class);

		assertCounter(routeFailureMetricsResponse, EtlRouteMetrics.MESSAGE_COUNTER);
		assertCounter(failureEventMetricsResponse, EtlFailureEventMetrics.PUBLISH_COUNTER);
		assertThat(prometheusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(prometheusResponse.getBody())
			.contains("realtime_etl_route_messages_total")
			.contains("realtime_etl_failure_events_total")
			.contains("route=\"orderInvoice\"")
			.contains("node=\"validateOrder\"")
			.contains("result=\"accepted\"")
			.contains("result=\"failure\"");
	}

	private static void assertCounter(ResponseEntity<JsonNode> response, String expectedName) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		JsonNode body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body.path("name").asText()).isEqualTo(expectedName);
		assertThat(body.path("measurements"))
			.anySatisfy(measurement -> {
				assertThat(measurement.path("statistic").asText()).isEqualTo("COUNT");
				assertThat(measurement.path("value").asDouble()).isGreaterThanOrEqualTo(1.0);
			});
	}
}
