package com.example.realtimeetl.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EtlContextTest {

	@Test
	void storesRouteScopedAttributes() {
		EtlContext context = new EtlContext("routeA");

		context.put("originalAmount", "12.50");

		assertThat(context.routeName()).isEqualTo("routeA");
		assertThat(context.get("originalAmount")).contains("12.50");
		assertThat(context.require("originalAmount", String.class)).isEqualTo("12.50");
		assertThat(context.attributes()).containsEntry("originalAmount", "12.50");
		assertThatThrownBy(() -> context.attributes().put("other", "value"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void rejectsBlankRouteName() {
		assertThatThrownBy(() -> new EtlContext(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("routeName must not be blank");
	}

	@Test
	void rejectsInvalidAttributes() {
		EtlContext context = new EtlContext("routeA");

		assertThatThrownBy(() -> context.put(" ", "value"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context key must not be blank");
		assertThatThrownBy(() -> context.put("key", null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("context value must not be null");
		assertThatThrownBy(() -> context.get(" "))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context key must not be blank");
	}

	@Test
	void rejectsMissingRequiredContextKey() {
		EtlContext context = new EtlContext("routeA");

		assertThatThrownBy(() -> context.require("missing", String.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Route 'routeA' is missing required context key 'missing'");
	}

	@Test
	void rejectsRequiredContextTypeMismatch() {
		EtlContext context = new EtlContext("routeA");
		context.put("riskScore", "high");

		assertThatThrownBy(() -> context.require("riskScore", Integer.class))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Route 'routeA' context key 'riskScore' expected java.lang.Integer but found java.lang.String");
	}

	@Test
	void rejectsInvalidRequiredContextArguments() {
		EtlContext context = new EtlContext("routeA");

		assertThatThrownBy(() -> context.require(" ", String.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("context key must not be blank");
		assertThatThrownBy(() -> context.require("key", null))
			.isInstanceOf(NullPointerException.class)
			.hasMessage("context type must not be null");
	}
}
