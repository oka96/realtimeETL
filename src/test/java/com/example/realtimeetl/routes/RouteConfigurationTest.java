package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.example.realtimeetl.analytics.OrderAnalyticsEvent;
import com.example.realtimeetl.etl.EtlHeaders;
import com.example.realtimeetl.etl.EtlRouteRegistry;
import com.example.realtimeetl.fraud.FraudReviewEvent;
import com.example.realtimeetl.invoice.InvoiceEvent;
import com.example.realtimeetl.order.OrderCreated;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class RouteConfigurationTest {

	@Test
	void registersMultipleRoutesWithMultipleNodes() {
		RouteConfiguration configuration = new RouteConfiguration();
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(
			configuration.orderInvoiceRoute(defaultInvoiceProperties()),
			configuration.fraudReviewRoute(defaultFraudRiskProperties()),
			configuration.orderAnalyticsRoute(defaultAnalyticsBandProperties())));

		assertThat(registry.routes())
			.extracting("name")
			.containsExactlyInAnyOrder(
				RouteNames.ORDER_INVOICE,
				RouteNames.FRAUD_REVIEW,
				RouteNames.ORDER_ANALYTICS);

		assertThat(registry.required(RouteNames.ORDER_INVOICE).nodes())
			.extracting("name")
			.containsExactly("requireOrderContract", "validateOrder", "normalizeOrder", "buildInvoice", "markInvoiceRoute");
		assertThat(registry.required(RouteNames.FRAUD_REVIEW).nodes())
			.extracting("name")
			.containsExactly(
				"requireOrderContract",
				"validateOrder",
				"normalizeOrder",
				"scoreRisk",
				"buildReview",
				"markFraudRoute");
		assertThat(registry.required(RouteNames.ORDER_ANALYTICS).nodes())
			.extracting("name")
			.containsExactly(
				"requireOrderContract",
				"validateOrder",
				"normalizeOrder",
				"classifyAmount",
				"buildAnalytics",
				"markAnalyticsRoute");
	}

	@Test
	void exposesProductionRouteCatalogContracts() {
		RouteConfiguration configuration = new RouteConfiguration();
		EtlRouteRegistry registry = new EtlRouteRegistry(List.of(
			configuration.orderInvoiceRoute(defaultInvoiceProperties()),
			configuration.fraudReviewRoute(defaultFraudRiskProperties()),
			configuration.orderAnalyticsRoute(defaultAnalyticsBandProperties())));

		assertThat(registry.definitions())
			.extracting(
				"routeName",
				"inputType",
				"outputType",
				"inputBindingName",
				"outputBindingName",
				"nodeNames",
				"inputContractHeaders",
				"contractHeaders")
			.containsExactlyInAnyOrder(
				tuple(
					RouteNames.ORDER_INVOICE,
					OrderCreated.class.getName(),
					InvoiceEvent.class.getName(),
					"orderInvoice-in-0",
					"orderInvoice-out-0",
					List.of("requireOrderContract", "validateOrder", "normalizeOrder", "buildInvoice", "markInvoiceRoute"),
					Map.of(
						EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED,
						EtlHeaders.EVENT_VERSION, RouteEventVersions.V1),
					Map.of(
						EtlHeaders.EVENT_TYPE, RouteEventTypes.INVOICE,
						EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)),
				tuple(
					RouteNames.FRAUD_REVIEW,
					OrderCreated.class.getName(),
					FraudReviewEvent.class.getName(),
					"fraudReview-in-0",
					"fraudReview-out-0",
					List.of(
						"requireOrderContract",
						"validateOrder",
						"normalizeOrder",
						"scoreRisk",
						"buildReview",
						"markFraudRoute"),
					Map.of(
						EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED,
						EtlHeaders.EVENT_VERSION, RouteEventVersions.V1),
					Map.of(
						EtlHeaders.EVENT_TYPE, RouteEventTypes.FRAUD_REVIEW,
						EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)),
				tuple(
					RouteNames.ORDER_ANALYTICS,
					OrderCreated.class.getName(),
					OrderAnalyticsEvent.class.getName(),
					"orderAnalytics-in-0",
					"orderAnalytics-out-0",
					List.of(
						"requireOrderContract",
						"validateOrder",
						"normalizeOrder",
						"classifyAmount",
						"buildAnalytics",
						"markAnalyticsRoute"),
					Map.of(
						EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED,
						EtlHeaders.EVENT_VERSION, RouteEventVersions.V1),
					Map.of(
						EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_ANALYTICS,
						EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)));
	}

	@Test
	void invoiceRouteUsesConfiguredInvoicePolicy() {
		RouteConfiguration configuration = new RouteConfiguration();
		OrderCreated order = new OrderCreated(
			"O-9001",
			"C-77",
			new BigDecimal("42.50"),
			"MYR");

		Message<?> output = configuration.orderInvoiceRoute(new InvoiceRouteProperties("BILL-", "PENDING"))
			.execute(MessageBuilder.withPayload(order)
				.setHeader(EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED)
				.setHeader(EtlHeaders.EVENT_VERSION, RouteEventVersions.V1)
				.build());

		assertThat(output.getPayload()).isEqualTo(new InvoiceEvent(
			"BILL-O-9001",
			"O-9001",
			"C-77",
			new BigDecimal("42.50"),
			"MYR",
			"PENDING"));
	}

	private static InvoiceRouteProperties defaultInvoiceProperties() {
		return new InvoiceRouteProperties("INV-", "READY");
	}

	private static FraudRiskProperties defaultFraudRiskProperties() {
		return new FraudRiskProperties(
			15,
			new BigDecimal("500.00"),
			45,
			"MYR",
			20,
			60);
	}

	private static AnalyticsBandProperties defaultAnalyticsBandProperties() {
		return new AnalyticsBandProperties(
			new BigDecimal("100.00"),
			new BigDecimal("500.00"));
	}
}
