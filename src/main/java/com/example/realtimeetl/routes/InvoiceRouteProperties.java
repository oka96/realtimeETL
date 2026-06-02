package com.example.realtimeetl.routes;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Validated
@ConfigurationProperties(prefix = "realtime-etl.invoice")
public record InvoiceRouteProperties(
	@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9]*-") String invoiceIdPrefix,
	@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]*") String status
) {

	private static final java.util.regex.Pattern INVOICE_ID_PREFIX_PATTERN =
		java.util.regex.Pattern.compile("[A-Z][A-Z0-9]*-");
	private static final java.util.regex.Pattern STATUS_PATTERN =
		java.util.regex.Pattern.compile("[A-Z][A-Z0-9_]*");

	public InvoiceRouteProperties {
		invoiceIdPrefix = requireText(invoiceIdPrefix, "invoiceIdPrefix must not be blank");
		if (!INVOICE_ID_PREFIX_PATTERN.matcher(invoiceIdPrefix).matches()) {
			throw new IllegalArgumentException("invoiceIdPrefix must match [A-Z][A-Z0-9]*-");
		}
		status = requireText(status, "status must not be blank");
		if (!STATUS_PATTERN.matcher(status).matches()) {
			throw new IllegalArgumentException("status must match [A-Z][A-Z0-9_]*");
		}
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
