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

class FraudRiskPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
		.withUserConfiguration(TestConfiguration.class);

	@Test
	void bindsValidFraudRiskProperties() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.fraud-risk.base-score=12",
				"realtime-etl.fraud-risk.high-amount-threshold=250.00",
				"realtime-etl.fraud-risk.high-amount-score=35",
				"realtime-etl.fraud-risk.domestic-currency= myr ",
				"realtime-etl.fraud-risk.foreign-currency-score=18",
				"realtime-etl.fraud-risk.manual-review-threshold=50")
			.run(context -> {
				assertThat(context).hasSingleBean(FraudRiskProperties.class);
				FraudRiskProperties properties = context.getBean(FraudRiskProperties.class);
				assertThat(properties.baseScore()).isEqualTo(12);
				assertThat(properties.highAmountThreshold()).isEqualByComparingTo(new BigDecimal("250.00"));
				assertThat(properties.highAmountScore()).isEqualTo(35);
				assertThat(properties.domesticCurrency()).isEqualTo("MYR");
				assertThat(properties.foreignCurrencyScore()).isEqualTo(18);
				assertThat(properties.manualReviewThreshold()).isEqualTo(50);
			});
	}

	@Test
	void directConstructionNormalizesAndValidatesFraudRiskProperties() {
		FraudRiskProperties properties = new FraudRiskProperties(
			12,
			new BigDecimal("250.00"),
			35,
			" myr ",
			18,
			50);

		assertThat(properties.domesticCurrency()).isEqualTo("MYR");
		assertThatNullPointerException()
			.isThrownBy(() -> new FraudRiskProperties(12, null, 35, "MYR", 18, 50))
			.withMessage("highAmountThreshold must not be null");
		assertThatThrownBy(() -> new FraudRiskProperties(12, BigDecimal.ZERO, 35, "MYR", 18, 50))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("highAmountThreshold must be positive");
		assertThatThrownBy(() -> new FraudRiskProperties(12, new BigDecimal("250.00"), 35, "M1", 18, 50))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("domesticCurrency must be an uppercase three-letter code");
		assertThatThrownBy(() -> new FraudRiskProperties(10, new BigDecimal("250.00"), 35, "MYR", 18, 64))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("manualReviewThreshold must be less than or equal to maximum reachable risk score");
	}

	@Test
	void rejectsInvalidFraudRiskProperties() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.fraud-risk.base-score=12",
				"realtime-etl.fraud-risk.high-amount-threshold=0",
				"realtime-etl.fraud-risk.high-amount-score=35",
				"realtime-etl.fraud-risk.domestic-currency=",
				"realtime-etl.fraud-risk.foreign-currency-score=18",
				"realtime-etl.fraud-risk.manual-review-threshold=50")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.fraud-risk")
					.hasRootCauseMessage("highAmountThreshold must be positive");
			});
	}

	@Test
	void rejectsMalformedDomesticCurrency() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.fraud-risk.base-score=12",
				"realtime-etl.fraud-risk.high-amount-threshold=250.00",
				"realtime-etl.fraud-risk.high-amount-score=35",
				"realtime-etl.fraud-risk.domestic-currency=M1",
				"realtime-etl.fraud-risk.foreign-currency-score=18",
				"realtime-etl.fraud-risk.manual-review-threshold=50")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.fraud-risk")
					.hasRootCauseMessage("domesticCurrency must be an uppercase three-letter code");
			});
	}

	@Test
	void rejectsManualReviewThresholdAboveMaximumReachableRiskScore() {
		contextRunner
			.withPropertyValues(
				"realtime-etl.fraud-risk.base-score=10",
				"realtime-etl.fraud-risk.high-amount-threshold=250.00",
				"realtime-etl.fraud-risk.high-amount-score=35",
				"realtime-etl.fraud-risk.domestic-currency=MYR",
				"realtime-etl.fraud-risk.foreign-currency-score=18",
				"realtime-etl.fraud-risk.manual-review-threshold=64")
			.run(context -> {
				assertThat(context).hasFailed();
				assertThat(context.getStartupFailure())
					.hasMessageContaining("realtime-etl.fraud-risk")
					.hasRootCauseMessage(
						"manualReviewThreshold must be less than or equal to maximum reachable risk score");
			});
	}

	@EnableConfigurationProperties(FraudRiskProperties.class)
	static class TestConfiguration {
	}
}
