package com.example.realtimeetl.routes;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "realtime-etl.fraud-risk")
public record FraudRiskProperties(
	@Min(0) int baseScore,
	@NotNull @Positive BigDecimal highAmountThreshold,
	@Min(0) int highAmountScore,
	@NotBlank @Pattern(regexp = "[A-Z]{3}") String domesticCurrency,
	@Min(0) int foreignCurrencyScore,
	@Min(0) int manualReviewThreshold
) {

	private static final java.util.regex.Pattern CURRENCY_PATTERN = java.util.regex.Pattern.compile("[A-Z]{3}");

	public FraudRiskProperties {
		if (baseScore < 0) {
			throw new IllegalArgumentException("baseScore must not be negative");
		}
		Objects.requireNonNull(highAmountThreshold, "highAmountThreshold must not be null");
		if (highAmountThreshold.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("highAmountThreshold must be positive");
		}
		if (highAmountScore < 0) {
			throw new IllegalArgumentException("highAmountScore must not be negative");
		}
		if (domesticCurrency == null || domesticCurrency.isBlank()) {
			throw new IllegalArgumentException("domesticCurrency must not be blank");
		}
		domesticCurrency = domesticCurrency.trim().toUpperCase(Locale.ROOT);
		if (!CURRENCY_PATTERN.matcher(domesticCurrency).matches()) {
			throw new IllegalArgumentException("domesticCurrency must be an uppercase three-letter code");
		}
		if (foreignCurrencyScore < 0) {
			throw new IllegalArgumentException("foreignCurrencyScore must not be negative");
		}
		if (manualReviewThreshold < 0) {
			throw new IllegalArgumentException("manualReviewThreshold must not be negative");
		}
		if (manualReviewThreshold > baseScore + highAmountScore + foreignCurrencyScore) {
			throw new IllegalArgumentException(
				"manualReviewThreshold must be less than or equal to maximum reachable risk score");
		}
	}

	@AssertTrue(message = "manualReviewThreshold must be less than or equal to maximum reachable risk score")
	boolean isManualReviewThresholdReachable() {
		return manualReviewThreshold <= baseScore + highAmountScore + foreignCurrencyScore;
	}
}
