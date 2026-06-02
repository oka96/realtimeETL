package com.example.realtimeetl.etl;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public final class EtlRoutesInfoContributor implements InfoContributor {

	private final EtlRouteRegistry registry;
	private final EtlRouteCatalogDetails catalogDetails;

	public EtlRoutesInfoContributor(EtlRouteRegistry registry, EtlRouteCatalogDetails catalogDetails) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.catalogDetails = Objects.requireNonNull(catalogDetails, "catalogDetails must not be null");
	}

	@Override
	public void contribute(Info.Builder builder) {
		Objects.requireNonNull(builder, "builder must not be null");
		List<EtlRouteDefinition> definitions = registry.definitions();
		builder.withDetail("etl", Map.of(
			"failureBinding", catalogDetails.failureBindingDetails(),
			"routeCount", definitions.size(),
			"routes", definitions.stream()
				.map(catalogDetails::routeDetails)
				.toList()));
	}
}
