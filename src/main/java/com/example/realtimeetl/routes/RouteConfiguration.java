package com.example.realtimeetl.routes;

import java.math.RoundingMode;
import java.util.Map;
import java.util.function.Function;

import com.example.realtimeetl.analytics.OrderAnalyticsEvent;
import com.example.realtimeetl.etl.EtlHeaders;
import com.example.realtimeetl.etl.EtlRoute;
import com.example.realtimeetl.etl.EtlRouteMetrics;
import com.example.realtimeetl.etl.EtlRouteRegistry;
import com.example.realtimeetl.fraud.FraudReviewEvent;
import com.example.realtimeetl.invoice.InvoiceEvent;
import com.example.realtimeetl.order.OrderCreated;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.Message;

@Configuration
@EnableConfigurationProperties({
	InvoiceRouteProperties.class,
	FraudRiskProperties.class,
	AnalyticsBandProperties.class
})
public class RouteConfiguration {

	@Bean
	EtlRoute orderInvoiceRoute(InvoiceRouteProperties invoiceRouteProperties) {
		return EtlRoute.builder(RouteNames.ORDER_INVOICE)
			.payloadTypes(OrderCreated.class, InvoiceEvent.class)
			.requireInputHeaders("requireOrderContract", Map.of(
				EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED,
				EtlHeaders.EVENT_VERSION, RouteEventVersions.V1))
			.node("validateOrder", new OrderValidationNode())
			.node("normalizeOrder", new OrderNormalizationNode())
			.transformPayload(
				"buildInvoice",
				OrderCreated.class,
				InvoiceEvent.class,
				order -> new InvoiceEvent(
					invoiceRouteProperties.invoiceIdPrefix() + order.orderId(),
					order.orderId(),
					order.customerId(),
					order.amount(),
					order.currency(),
					invoiceRouteProperties.status()))
			.enrichHeaders("markInvoiceRoute", Map.of(
				EtlHeaders.EVENT_TYPE, RouteEventTypes.INVOICE,
				EtlHeaders.EVENT_VERSION, RouteEventVersions.V1))
			.build();
	}

	@Bean
	EtlRoute fraudReviewRoute(FraudRiskProperties fraudRiskProperties) {
		return EtlRoute.builder(RouteNames.FRAUD_REVIEW)
			.payloadTypes(OrderCreated.class, FraudReviewEvent.class)
			.requireInputHeaders("requireOrderContract", Map.of(
				EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED,
				EtlHeaders.EVENT_VERSION, RouteEventVersions.V1))
			.node("validateOrder", new OrderValidationNode())
			.node("normalizeOrder", new OrderNormalizationNode())
			.node("scoreRisk", new RiskScoreNode(fraudRiskProperties))
			.transformPayload("buildReview", OrderCreated.class, FraudReviewEvent.class, (order, context) -> {
				int riskScore = context.require(RiskScoreNode.CONTEXT_KEY, Integer.class);
				String decision = riskScore >= fraudRiskProperties.manualReviewThreshold()
					? "MANUAL_REVIEW"
					: "AUTO_APPROVE";
				return new FraudReviewEvent(
					"FR-" + order.orderId(),
					order.orderId(),
					order.customerId(),
					order.amount().setScale(2, RoundingMode.HALF_UP),
					riskScore,
					decision);
			})
			.enrichHeaders("markFraudRoute", Map.of(
				EtlHeaders.EVENT_TYPE, RouteEventTypes.FRAUD_REVIEW,
				EtlHeaders.EVENT_VERSION, RouteEventVersions.V1))
			.build();
	}

	@Bean
	EtlRoute orderAnalyticsRoute(AnalyticsBandProperties analyticsBandProperties) {
		return EtlRoute.builder(RouteNames.ORDER_ANALYTICS)
			.payloadTypes(OrderCreated.class, OrderAnalyticsEvent.class)
			.requireInputHeaders("requireOrderContract", Map.of(
				EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_CREATED,
				EtlHeaders.EVENT_VERSION, RouteEventVersions.V1))
			.node("validateOrder", new OrderValidationNode())
			.node("normalizeOrder", new OrderNormalizationNode())
			.node("classifyAmount", new AmountBandNode(
				analyticsBandProperties.mediumThreshold(),
				analyticsBandProperties.largeThreshold()))
			.transformPayload("buildAnalytics", OrderCreated.class, OrderAnalyticsEvent.class, (order, context) ->
				new OrderAnalyticsEvent(
					order.orderId(),
					order.customerId(),
					order.amount().setScale(2, RoundingMode.HALF_UP),
					order.currency(),
					context.require(AmountBandNode.CONTEXT_KEY, String.class)))
			.enrichHeaders("markAnalyticsRoute", Map.of(
				EtlHeaders.EVENT_TYPE, RouteEventTypes.ORDER_ANALYTICS,
				EtlHeaders.EVENT_VERSION, RouteEventVersions.V1))
			.build();
	}

	@Bean
	Function<Message<OrderCreated>, Message<InvoiceEvent>> orderInvoice(
		EtlRouteRegistry registry,
		EtlRouteMetrics metrics) {
		return metrics.instrument(
			RouteNames.ORDER_INVOICE,
			registry.required(RouteNames.ORDER_INVOICE).toFunction(OrderCreated.class, InvoiceEvent.class));
	}

	@Bean
	Function<Message<OrderCreated>, Message<FraudReviewEvent>> fraudReview(
		EtlRouteRegistry registry,
		EtlRouteMetrics metrics) {
		return metrics.instrument(
			RouteNames.FRAUD_REVIEW,
			registry.required(RouteNames.FRAUD_REVIEW).toFunction(OrderCreated.class, FraudReviewEvent.class));
	}

	@Bean
	Function<Message<OrderCreated>, Message<OrderAnalyticsEvent>> orderAnalytics(
		EtlRouteRegistry registry,
		EtlRouteMetrics metrics) {
		return metrics.instrument(
			RouteNames.ORDER_ANALYTICS,
			registry.required(RouteNames.ORDER_ANALYTICS).toFunction(OrderCreated.class, OrderAnalyticsEvent.class));
	}

}
