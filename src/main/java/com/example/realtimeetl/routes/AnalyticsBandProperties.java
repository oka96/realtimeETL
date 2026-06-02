package com.example.realtimeetl.routes;

import java.math.BigDecimal;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "realtime-etl.analytics-band")
public record AnalyticsBandProperties(
	@NotNull @Positive BigDecimal mediumThreshold,
	@NotNull @Positive BigDecimal largeThreshold
) {

	public AnalyticsBandProperties {
		Objects.requireNonNull(mediumThreshold, "mediumThreshold must not be null");
		Objects.requireNonNull(largeThreshold, "largeThreshold must not be null");
		if (mediumThreshold.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("mediumThreshold must be positive");
		}
		if (largeThreshold.compareTo(BigDecimal.ZERO) <= 0) {
			throw new IllegalArgumentException("largeThreshold must be positive");
		}
		if (largeThreshold.compareTo(mediumThreshold) <= 0) {
			throw new IllegalArgumentException("largeThreshold must be greater than mediumThreshold");
		}
	}

	@AssertTrue(message = "largeThreshold must be greater than mediumThreshold")
	boolean isLargeThresholdGreaterThanMediumThreshold() {
		return mediumThreshold != null
			&& largeThreshold != null
			&& largeThreshold.compareTo(mediumThreshold) > 0;
	}
}
