package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AnalyticsBandPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void bindsValidAnalyticsBandProperties() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.analytics-band.medium-threshold=125.00",
				"realtime-etl.analytics-band.large-threshold=750.00")
			.run(context -> {
				assertThat(context).hasSingleBean(AnalyticsBandProperties.class);
				AnalyticsBandProperties properties = context.getBean(AnalyticsBandProperties.class);
				assertThat(properties.mediumThreshold()).isEqualByComparingTo(new BigDecimal("125.00"));
				assertThat(properties.largeThreshold()).isEqualByComparingTo(new BigDecimal("750.00"));
			});
	}

	@Test
	void directConstructionValidatesAnalyticsBandProperties() {
		AnalyticsBandProperties properties = new AnalyticsBandProperties(
			new BigDecimal("125.00"),
			new BigDecimal("750.00"));

		assertThat(properties.mediumThreshold()).isEqualByComparingTo("125.00");
		assertThat(properties.largeThreshold()).isEqualByComparingTo("750.00");
		assertThatNullPointerException()
			.isThrownBy(() -> new AnalyticsBandProperties(null, new BigDecimal("750.00")))
			.withMessage("mediumThreshold must not be null");
		assertThatThrownBy(() -> new AnalyticsBandProperties(BigDecimal.ZERO, new BigDecimal("750.00")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("mediumThreshold must be positive");
		assertThatThrownBy(() -> new AnalyticsBandProperties(new BigDecimal("500.00"), new BigDecimal("100.00")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("largeThreshold must be greater than mediumThreshold");
	}

	@Test
	void rejectsNonPositiveThresholds() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.analytics-band.medium-threshold=0",
				"realtime-etl.analytics-band.large-threshold=500.00")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.analytics-band")
					.hasRootCauseMessage("mediumThreshold must be positive");
			});
	}

	@Test
	void rejectsLargeThresholdNotGreaterThanMediumThreshold() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.analytics-band.medium-threshold=500.00",
				"realtime-etl.analytics-band.large-threshold=100.00")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.analytics-band")
					.hasRootCauseMessage("largeThreshold must be greater than mediumThreshold");
			});
	}

	@EnableConfigurationProperties(AnalyticsBandProperties.class)
	static class TestConfiguration {
	}
}
