package com.example.realtimeetl.etl;

import java.time.Clock;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.support.ErrorMessage;
import org.springframework.stereotype.Component;

@Component
public final class EtlRouteErrorHandler {

	private static final Logger log = LoggerFactory.getLogger(EtlRouteErrorHandler.class);

	private final EtlFailureEventPublisher failureEventPublisher;
	private final EtlFailureEventMetrics failureEventMetrics;
	private final Clock clock;

	@Autowired
	public EtlRouteErrorHandler(
		EtlFailureEventPublisher failureEventPublisher,
		EtlFailureEventMetrics failureEventMetrics) {
		this(failureEventPublisher, failureEventMetrics, Clock.systemUTC());
	}

	EtlRouteErrorHandler(
		EtlFailureEventPublisher failureEventPublisher,
		EtlFailureEventMetrics failureEventMetrics,
		Clock clock) {
		this.failureEventPublisher = Objects.requireNonNull(
			failureEventPublisher,
			"failureEventPublisher must not be null");
		this.failureEventMetrics = Objects.requireNonNull(failureEventMetrics, "failureEventMetrics must not be null");
		this.clock = Objects.requireNonNull(clock, "clock must not be null");
	}

	@ServiceActivator(inputChannel = "errorChannel")
	public void handle(ErrorMessage errorMessage) {
		Throwable error = errorMessage.getPayload();
		EtlRouteExecutionException routeFailure = findRouteFailure(error);
		if (routeFailure != null) {
			log.error(
				"ETL route failure route={} node={} trace={} inputPayloadType={} sourceMessageId={} "
					+ "sourceMessageTimestamp={} completedNodes={} nodeDurationsNanos={}",
				routeFailure.routeName(),
				routeFailure.nodeName(),
				routeFailure.traceId(),
				routeFailure.inputPayloadType(),
				routeFailure.sourceMessageId(),
				routeFailure.sourceMessageTimestamp(),
				routeFailure.completedNodeCount(),
				routeFailure.nodeDurationsNanos(),
				routeFailure);
			publishFailureEvent(routeFailure);
			return;
		}

		log.error("Unhandled stream processing error: {}", error.getMessage(), error);
	}

	private void publishFailureEvent(EtlRouteExecutionException routeFailure) {
		try {
			EtlFailureEvent event = EtlFailureEvent.from(routeFailure, clock.millis());
			boolean accepted = failureEventPublisher.publish(event);
			failureEventMetrics.record(
				routeFailure,
				accepted ? EtlFailureEventMetrics.ACCEPTED : EtlFailureEventMetrics.REJECTED);
			if (!accepted) {
				log.warn(
					"ETL route failure event was not accepted by binding {} for route={} node={} trace={}",
					EtlFailureEventPublisher.BINDING_NAME,
					routeFailure.routeName(),
					routeFailure.nodeName(),
					routeFailure.traceId());
			}
		}
		catch (RuntimeException ex) {
			failureEventMetrics.record(routeFailure, EtlFailureEventMetrics.ERROR);
			log.warn(
				"Failed to publish ETL route failure event to binding {} for route={} node={} trace={}",
				EtlFailureEventPublisher.BINDING_NAME,
				routeFailure.routeName(),
				routeFailure.nodeName(),
				routeFailure.traceId(),
				ex);
		}
	}

	private static EtlRouteExecutionException findRouteFailure(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof EtlRouteExecutionException routeFailure) {
				return routeFailure;
			}
			Throwable cause = current.getCause();
			if (cause == current) {
				return null;
			}
			current = cause;
		}
		return null;
	}
}
