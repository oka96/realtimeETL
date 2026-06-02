package com.example.realtimeetl.etl;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class EtlRouteCatalogLogger implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EtlRouteCatalogLogger.class);

	private final EtlRouteRegistry registry;
	private final Environment environment;

	public EtlRouteCatalogLogger(EtlRouteRegistry registry, Environment environment) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.environment = Objects.requireNonNull(environment, "environment must not be null");
	}

	@Override
	public void run(ApplicationArguments args) {
		log.info("Configured {} ETL route(s)", registry.definitions().size());
		for (EtlRouteDefinition definition : registry.definitions()) {
			log.info(
				"ETL route '{}' maps {} -> {} with input contract headers {} and output contract headers {} "
					+ "and {} node(s): {}; "
					+ "input {} -> '{}' group '{}' retry maxAttempts={} backOffInitialInterval={} "
					+ "backOffMaxInterval={}; output {} -> '{}'",
				definition.routeName(),
				definition.inputType(),
				definition.outputType(),
				definition.inputContractHeaders(),
				definition.contractHeaders(),
				definition.nodeCount(),
				definition.nodeNames(),
				definition.inputBindingName(),
				bindingProperty(definition.inputBindingName(), "destination"),
				bindingProperty(definition.inputBindingName(), "group"),
				bindingProperty(definition.inputBindingName(), "consumer.max-attempts"),
				bindingProperty(definition.inputBindingName(), "consumer.back-off-initial-interval"),
				bindingProperty(definition.inputBindingName(), "consumer.back-off-max-interval"),
				definition.outputBindingName(),
				bindingProperty(definition.outputBindingName(), "destination"));
		}
		log.info(
			"ETL failure events output {} -> '{}'",
			EtlFailureEventPublisher.BINDING_NAME,
			bindingProperty(EtlFailureEventPublisher.BINDING_NAME, "destination"));
	}

	private String bindingProperty(String bindingName, String propertyName) {
		String value = environment.getProperty("spring.cloud.stream.bindings.%s.%s".formatted(bindingName, propertyName));
		return value == null || value.isBlank() ? "not configured" : value;
	}
}
