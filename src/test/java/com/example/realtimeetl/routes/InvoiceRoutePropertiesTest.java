package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InvoiceRoutePropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void bindsValidInvoiceRouteProperties() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.invoice.invoice-id-prefix= BILL- ",
				"realtime-etl.invoice.status= PENDING ")
			.run(context -> {
				assertThat(context).hasSingleBean(InvoiceRouteProperties.class);
				InvoiceRouteProperties properties = context.getBean(InvoiceRouteProperties.class);
				assertThat(properties.invoiceIdPrefix()).isEqualTo("BILL-");
				assertThat(properties.status()).isEqualTo("PENDING");
			});
	}

	@Test
	void directConstructionTrimsAndValidatesInvoiceRouteProperties() {
		InvoiceRouteProperties properties = new InvoiceRouteProperties(" BILL- ", " PENDING ");

		assertThat(properties.invoiceIdPrefix()).isEqualTo("BILL-");
		assertThat(properties.status()).isEqualTo("PENDING");
		assertThatThrownBy(() -> new InvoiceRouteProperties(" ", "READY"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("invoiceIdPrefix must not be blank");
		assertThatThrownBy(() -> new InvoiceRouteProperties("inv", "READY"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("invoiceIdPrefix must match [A-Z][A-Z0-9]*-");
		assertThatThrownBy(() -> new InvoiceRouteProperties("INV-", "ready"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("status must match [A-Z][A-Z0-9_]*");
	}

	@Test
	void rejectsBlankInvoiceRouteProperties() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.invoice.invoice-id-prefix= ",
				"realtime-etl.invoice.status= ")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.invoice")
					.hasRootCauseMessage("invoiceIdPrefix must not be blank");
			});
	}

	@Test
	void rejectsMalformedInvoiceStatus() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.invoice.invoice-id-prefix=INV-",
				"realtime-etl.invoice.status=ready")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.invoice")
					.hasRootCauseMessage("status must match [A-Z][A-Z0-9_]*");
			});
	}

	@Test
	void rejectsMalformedInvoiceIdPrefix() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.invoice.invoice-id-prefix=inv",
				"realtime-etl.invoice.status=READY")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.invoice")
					.hasRootCauseMessage("invoiceIdPrefix must match [A-Z][A-Z0-9]*-");
			});
	}

	@EnableConfigurationProperties(InvoiceRouteProperties.class)
	static class TestConfiguration {
	}
}
