package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

class EtlRouteMetricsTest {

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final EtlRouteMetrics metrics = new EtlRouteMetrics(meterRegistry);

	@Test
	void rejectsMissingMeterRegistry() {
		assertThatNullPointerException()
			.isThrownBy(() -> new EtlRouteMetrics(null))
			.withMessage("meterRegistry must not be null");
	}

	@Test
	void recordsSuccessfulRouteMessagesAndDuration() {
		Function<Message<String>, Message<String>> instrumented = metrics.instrument(
			"metricsRoute",
			message -> MessageBuilder.withPayload(message.getPayload().toUpperCase()).build());

		Message<String> output = instrumented.apply(MessageBuilder.withPayload("abc").build());

		assertThat(output.getPayload()).isEqualTo("ABC");
		assertThat(counter("metricsRoute", "success")).isEqualTo(1.0);
		assertThat(timerCount("metricsRoute", "success")).isEqualTo(1);
		assertThat(counter("metricsRoute", "failure")).isZero();
		assertThat(timerCount("metricsRoute", "failure")).isZero();
	}

	@Test
	void recordsFailedRouteMessagesAndDurationBeforeRethrowing() {
		Function<Message<String>, Message<String>> instrumented = metrics.instrument("metricsRoute", message -> {
			throw new IllegalArgumentException("bad payload");
		});

		assertThatThrownBy(() -> instrumented.apply(MessageBuilder.withPayload("abc").build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("bad payload");
		assertThat(counter("metricsRoute", "failure")).isEqualTo(1.0);
		assertThat(timerCount("metricsRoute", "failure")).isEqualTo(1);
		assertThat(counter("metricsRoute", "success")).isZero();
		assertThat(timerCount("metricsRoute", "success")).isZero();
	}

	@Test
	void rejectsBlankRouteNames() {
		assertThatThrownBy(() -> metrics.instrument(" ", Function.identity()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
	}

	@Test
	void rejectsMissingDelegate() {
		assertThatNullPointerException()
			.isThrownBy(() -> metrics.instrument("metricsRoute", null))
			.withMessage("delegate must not be null");
	}

	private double counter(String routeName, String result) {
		Counter counter = meterRegistry.find(EtlRouteMetrics.MESSAGE_COUNTER)
			.tags("route", routeName, "result", result)
			.counter();
		return counter == null ? 0.0 : counter.count();
	}

	private long timerCount(String routeName, String result) {
		Timer timer = meterRegistry.find(EtlRouteMetrics.DURATION_TIMER)
			.tags("route", routeName, "result", result)
			.timer();
		return timer == null ? 0 : timer.count();
	}
}
