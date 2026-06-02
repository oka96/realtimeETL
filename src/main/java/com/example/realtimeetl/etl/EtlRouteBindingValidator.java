package com.example.realtimeetl.etl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

@Component
public final class EtlRouteBindingValidator implements ApplicationRunner {

	private static final String JSON_CONTENT_TYPE = "application/json";
	private static final String GENERIC_OBJECT_TYPE = Object.class.getName();
	private static final Pattern EVENT_TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9]*(?:-[a-z0-9]+)*");
	private static final Pattern EVENT_VERSION_PATTERN = Pattern.compile("v[0-9]+");

	private final EtlRouteRegistry registry;
	private final Environment environment;

	public EtlRouteBindingValidator(EtlRouteRegistry registry, Environment environment) {
		this.registry = Objects.requireNonNull(registry, "registry must not be null");
		this.environment = Objects.requireNonNull(environment, "environment must not be null");
	}

	@Override
	public void run(ApplicationArguments args) {
		List<String> functionNames = functionNames();
		List<String> routeNames = registry.definitions().stream()
			.map(EtlRouteDefinition::routeName)
			.toList();
		validateConfiguredFunctions(Set.copyOf(routeNames), functionNames);
		Map<String, String> outputDestinationOwners = new LinkedHashMap<>();
		Map<String, String> inputDestinationOwners = new LinkedHashMap<>();
		Map<OutputContract, String> outputContractOwners = new LinkedHashMap<>();
		Map<InputConsumer, String> inputConsumerOwners = new LinkedHashMap<>();
		for (EtlRouteDefinition definition : registry.definitions()) {
			String routeName = definition.routeName();
			validateExplicitPayloadTypes(definition);
			validateRequiredInputContractHeaders(definition);
			OutputContract outputContract = validateRequiredContractHeaders(definition);
			validateUniqueOutputContract(routeName, outputContract, outputContractOwners);
			validateFunctionDefinition(routeName, functionNames);
			InputConsumer inputConsumer = validateInputBinding(definition);
			String outputDestination = validateOutputBinding(definition);
			validateDistinctRouteDestinations(routeName, inputConsumer.destination(), outputDestination);
			trackInputDestination(routeName, inputConsumer.destination(), inputDestinationOwners);
			validateUniqueInputConsumer(routeName, inputConsumer, inputConsumerOwners);
			validateUniqueOutputDestination(routeName, outputDestination, outputDestinationOwners);
		}
		String failureDestination = validateFailureBinding();
		validateUniqueOutputDestination("etlFailures", failureDestination, outputDestinationOwners);
		validateFailureDestinationDoesNotFeedRouteInputs(failureDestination, inputDestinationOwners);
		validateFailureContractDoesNotOverlapRoutes(outputContractOwners);
		validateFunctionDefinitionOrder(routeNames, functionNames);
	}

	private List<String> functionNames() {
		String definition = environment.getProperty("spring.cloud.function.definition", "");
		Set<String> names = new LinkedHashSet<>();
		if (definition.isBlank()) {
			return List.of();
		}
		List<String> orderedNames = Arrays.stream(definition.split(";", -1))
			.map(String::trim)
			.toList();
		if (orderedNames.stream().anyMatch(String::isBlank)) {
			throw new IllegalStateException("spring.cloud.function.definition must not contain blank function entries");
		}
		orderedNames.forEach(name -> {
			if (!names.add(name)) {
				throw new IllegalStateException(
					"spring.cloud.function.definition contains duplicate function '%s'".formatted(name));
			}
		});
		return orderedNames;
	}

	private void validateFunctionDefinition(String routeName, List<String> functionNames) {
		if (!functionNames.contains(routeName)) {
			throw new IllegalStateException(
				"ETL route '%s' is missing from spring.cloud.function.definition".formatted(routeName));
		}
	}

	private void validateConfiguredFunctions(Set<String> routeNames, List<String> functionNames) {
		for (String functionName : functionNames) {
			if (!routeNames.contains(functionName)) {
				throw new IllegalStateException(
					"Spring function '%s' has no registered ETL route".formatted(functionName));
			}
		}
	}

	private void validateFunctionDefinitionOrder(List<String> routeNames, List<String> functionNames) {
		if (!functionNames.equals(routeNames)) {
			throw new IllegalStateException(
				"spring.cloud.function.definition order %s must match registered ETL route order %s"
					.formatted(functionNames, routeNames));
		}
	}

	private void validateExplicitPayloadTypes(EtlRouteDefinition definition) {
		if (GENERIC_OBJECT_TYPE.equals(definition.inputType())) {
			throw new IllegalStateException(
				"ETL route '%s' must declare a concrete input payload type".formatted(definition.routeName()));
		}
		if (GENERIC_OBJECT_TYPE.equals(definition.outputType())) {
			throw new IllegalStateException(
				"ETL route '%s' must declare a concrete output payload type".formatted(definition.routeName()));
		}
	}

	private OutputContract validateRequiredContractHeaders(EtlRouteDefinition definition) {
		validateContractHeader(definition, definition.contractHeaders(), "output", EtlHeaders.EVENT_TYPE);
		validateContractHeader(definition, definition.contractHeaders(), "output", EtlHeaders.EVENT_VERSION);
		return new OutputContract(
			(String) definition.contractHeaders().get(EtlHeaders.EVENT_TYPE),
			(String) definition.contractHeaders().get(EtlHeaders.EVENT_VERSION));
	}

	private void validateRequiredInputContractHeaders(EtlRouteDefinition definition) {
		validateContractHeader(definition, definition.inputContractHeaders(), "input", EtlHeaders.EVENT_TYPE);
		validateContractHeader(definition, definition.inputContractHeaders(), "input", EtlHeaders.EVENT_VERSION);
	}

	private void validateContractHeader(
		EtlRouteDefinition definition,
		Map<String, Object> headers,
		String direction,
		String headerName) {
		Object value = headers.get(headerName);
		if (!(value instanceof String text) || text.isBlank()) {
			throw new IllegalStateException(
				"ETL route '%s' must declare non-blank %s contract header %s"
					.formatted(definition.routeName(), direction, headerName));
		}
		validateNoSurroundingWhitespace(
			definition.routeName(),
			"%s contract header %s".formatted(direction, headerName),
			text);
		validateContractHeaderFormat(definition.routeName(), direction, headerName, text);
	}

	private void validateContractHeaderFormat(
		String routeName,
		String direction,
		String headerName,
		String value) {
		if (EtlHeaders.EVENT_TYPE.equals(headerName) && !EVENT_TYPE_PATTERN.matcher(value).matches()) {
			throw new IllegalStateException(
				"ETL route '%s' %s contract header %s must use lowercase kebab-case"
					.formatted(routeName, direction, headerName));
		}
		if (EtlHeaders.EVENT_VERSION.equals(headerName) && !EVENT_VERSION_PATTERN.matcher(value).matches()) {
			throw new IllegalStateException(
				"ETL route '%s' %s contract header %s must match v<digits>"
					.formatted(routeName, direction, headerName));
		}
	}

	private InputConsumer validateInputBinding(EtlRouteDefinition definition) {
		String routeName = definition.routeName();
		String bindingName = definition.inputBindingName();
		String destination = validateBindingDestination(routeName, bindingName);
		validateJsonContentType(routeName, bindingName);
		String groupProperty = bindingProperty(bindingName, "group");
		String group = environment.getProperty(groupProperty);
		if (missing(group)) {
			throw new IllegalStateException(
				"ETL route '%s' input binding must configure a durable consumer group at %s"
					.formatted(routeName, groupProperty));
		}
		validateNoSurroundingWhitespace(routeName, "binding property " + groupProperty, group);
		validateConsumerRetry(routeName, bindingName);
		return new InputConsumer(destination, group);
	}

	private String validateOutputBinding(EtlRouteDefinition definition) {
		String routeName = definition.routeName();
		String bindingName = definition.outputBindingName();
		String destination = validateBindingDestination(routeName, bindingName);
		validateJsonContentType(routeName, bindingName);
		return destination;
	}

	private String validateFailureBinding() {
		String bindingName = EtlFailureEventPublisher.BINDING_NAME;
		String destination = validateBindingDestination("etlFailures", bindingName);
		validateJsonContentType("etlFailures", bindingName);
		return destination;
	}

	private String validateBindingDestination(String routeName, String bindingName) {
		String destinationProperty = bindingProperty(bindingName, "destination");
		String destination = environment.getProperty(destinationProperty);
		if (missing(destination)) {
			throw new IllegalStateException(
				"ETL route '%s' is missing stream binding destination %s".formatted(routeName, destinationProperty));
		}
		validateNoSurroundingWhitespace(routeName, "binding property " + destinationProperty, destination);
		return destination;
	}

	private void validateDistinctRouteDestinations(String routeName, String inputDestination, String outputDestination) {
		if (inputDestination.equals(outputDestination)) {
			throw new IllegalStateException(
				"ETL route '%s' input and output bindings must use different destinations but both use '%s'"
					.formatted(routeName, inputDestination));
		}
	}

	private void validateUniqueInputConsumer(
		String routeName,
		InputConsumer inputConsumer,
		Map<InputConsumer, String> inputConsumerOwners) {
		String ownerRouteName = inputConsumerOwners.putIfAbsent(inputConsumer, routeName);
		if (ownerRouteName != null) {
			throw new IllegalStateException(
				"ETL route '%s' input destination '%s' and group '%s' are already used by route '%s'"
					.formatted(routeName, inputConsumer.destination(), inputConsumer.group(), ownerRouteName));
		}
	}

	private void trackInputDestination(
		String routeName,
		String inputDestination,
		Map<String, String> inputDestinationOwners) {
		inputDestinationOwners.putIfAbsent(inputDestination, routeName);
	}

	private void validateUniqueOutputDestination(
		String routeName,
		String outputDestination,
		Map<String, String> outputDestinationOwners) {
		String ownerRouteName = outputDestinationOwners.putIfAbsent(outputDestination, routeName);
		if (ownerRouteName != null) {
			throw new IllegalStateException(
				"ETL route '%s' output destination '%s' is already used by route '%s'"
					.formatted(routeName, outputDestination, ownerRouteName));
		}
	}

	private void validateUniqueOutputContract(
		String routeName,
		OutputContract outputContract,
		Map<OutputContract, String> outputContractOwners) {
		String ownerRouteName = outputContractOwners.putIfAbsent(outputContract, routeName);
		if (ownerRouteName != null) {
			throw new IllegalStateException(
				"ETL route '%s' output contract etlEventType='%s' etlEventVersion='%s' is already used by route '%s'"
					.formatted(routeName, outputContract.eventType(), outputContract.eventVersion(), ownerRouteName));
		}
	}

	private void validateFailureContractDoesNotOverlapRoutes(Map<OutputContract, String> outputContractOwners) {
		OutputContract failureContract = new OutputContract(
			EtlFailureEvent.EVENT_TYPE,
			EtlFailureEvent.EVENT_VERSION);
		String ownerRouteName = outputContractOwners.get(failureContract);
		if (ownerRouteName != null) {
			throw new IllegalStateException(
				"ETL route '%s' output contract etlEventType='%s' etlEventVersion='%s' is reserved for ETL failure events"
					.formatted(ownerRouteName, failureContract.eventType(), failureContract.eventVersion()));
		}
	}

	private void validateFailureDestinationDoesNotFeedRouteInputs(
		String failureDestination,
		Map<String, String> inputDestinationOwners) {
		String ownerRouteName = inputDestinationOwners.get(failureDestination);
		if (ownerRouteName != null) {
			throw new IllegalStateException(
				"ETL failure event destination '%s' must not match input destination used by route '%s'"
					.formatted(failureDestination, ownerRouteName));
		}
	}

	private void validateJsonContentType(String routeName, String bindingName) {
		String contentTypeProperty = bindingProperty(bindingName, "content-type");
		String contentType = environment.getProperty(contentTypeProperty);
		if (missing(contentType)) {
			throw new IllegalStateException(
				"ETL route '%s' binding %s must use %s content-type at %s"
					.formatted(routeName, bindingName, JSON_CONTENT_TYPE, contentTypeProperty));
		}
		validateNoSurroundingWhitespace(routeName, "binding property " + contentTypeProperty, contentType);
		if (!isApplicationJson(contentType)) {
			throw new IllegalStateException(
				"ETL route '%s' binding %s must use %s content-type at %s"
					.formatted(routeName, bindingName, JSON_CONTENT_TYPE, contentTypeProperty));
		}
	}

	private static boolean isApplicationJson(String contentType) {
		try {
			MimeType mimeType = MimeTypeUtils.parseMimeType(contentType);
			return MimeTypeUtils.APPLICATION_JSON.getType().equalsIgnoreCase(mimeType.getType())
				&& MimeTypeUtils.APPLICATION_JSON.getSubtype().equalsIgnoreCase(mimeType.getSubtype());
		}
		catch (InvalidMimeTypeException ex) {
			return false;
		}
	}

	private void validateConsumerRetry(String routeName, String bindingName) {
		int maxAttempts = positiveInt(routeName, consumerProperty(bindingName, "max-attempts"));
		if (maxAttempts < 2) {
			throw new IllegalStateException(
				"ETL route '%s' input binding retry property %s must be at least 2 to enable retries"
					.formatted(routeName, consumerProperty(bindingName, "max-attempts")));
		}
		int initialBackoff = positiveInt(routeName, consumerProperty(bindingName, "back-off-initial-interval"));
		int maxBackoff = positiveInt(routeName, consumerProperty(bindingName, "back-off-max-interval"));
		if (maxBackoff < initialBackoff) {
			throw new IllegalStateException(
				("ETL route '%s' input binding retry back-off-max-interval must be greater than or equal to "
					+ "back-off-initial-interval").formatted(routeName));
		}
	}

	private int positiveInt(String routeName, String propertyName) {
		String rawValue = environment.getProperty(propertyName);
		if (missing(rawValue)) {
			throw new IllegalStateException(
				"ETL route '%s' input binding must configure positive retry property %s"
					.formatted(routeName, propertyName));
		}
		validateNoSurroundingWhitespace(routeName, "binding property " + propertyName, rawValue);
		try {
			int value = Integer.parseInt(rawValue);
			if (value <= 0) {
				throw new IllegalStateException(
					"ETL route '%s' input binding retry property %s must be positive"
						.formatted(routeName, propertyName));
			}
			return value;
		}
		catch (NumberFormatException ex) {
			throw new IllegalStateException(
				"ETL route '%s' input binding retry property %s must be an integer"
					.formatted(routeName, propertyName),
				ex);
		}
	}

	private static String bindingProperty(String bindingName, String propertyName) {
		return "spring.cloud.stream.bindings.%s.%s".formatted(bindingName, propertyName);
	}

	private static String consumerProperty(String bindingName, String propertyName) {
		return bindingProperty(bindingName, "consumer." + propertyName);
	}

	private static boolean missing(String value) {
		return value == null || value.isBlank();
	}

	private static void validateNoSurroundingWhitespace(String routeName, String description, String value) {
		if (!value.equals(value.trim())) {
			throw new IllegalStateException(
				"ETL route '%s' %s must not contain surrounding whitespace"
					.formatted(routeName, description));
		}
	}

	private record InputConsumer(String destination, String group) {
	}

	private record OutputContract(String eventType, String eventVersion) {
	}
}
