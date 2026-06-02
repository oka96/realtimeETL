package com.example.realtimeetl.etl;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public record EtlRouteDefinition(
	String routeName,
	String inputType,
	String outputType,
	List<String> nodeNames,
	Map<String, Object> inputContractHeaders,
	Map<String, Object> contractHeaders
) {

	private static final Pattern ROUTE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");
	private static final Pattern NODE_NAME_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

	public EtlRouteDefinition {
		routeName = requireRouteName(routeName);
		inputType = requireText(inputType, "inputType must not be blank");
		outputType = requireText(outputType, "outputType must not be blank");
		nodeNames = immutableNodeNames(routeName, nodeNames);
		inputContractHeaders = immutableHeaders(inputContractHeaders, "inputContractHeaders");
		contractHeaders = immutableHeaders(contractHeaders, "contractHeaders");
	}

	public int nodeCount() {
		return nodeNames.size();
	}

	public String inputBindingName() {
		return routeName + "-in-0";
	}

	public String outputBindingName() {
		return routeName + "-out-0";
	}

	private static List<String> immutableNodeNames(String routeName, List<String> nodeNames) {
		Objects.requireNonNull(nodeNames, "nodeNames must not be null");
		if (nodeNames.isEmpty()) {
			throw new IllegalArgumentException("nodeNames must not be empty");
		}
		Set<String> names = new LinkedHashSet<>();
		List<String> requiredNodeNames = nodeNames.stream()
			.map(EtlRouteDefinition::requireNodeName)
			.toList();
		for (String nodeName : requiredNodeNames) {
			if (!names.add(nodeName)) {
				throw new IllegalArgumentException(
					"Route definition '%s' contains duplicate node name '%s'".formatted(routeName, nodeName));
			}
		}
		return requiredNodeNames;
	}

	private static Map<String, Object> immutableHeaders(Map<String, Object> headers, String description) {
		Objects.requireNonNull(headers, description + " must not be null");
		Map<String, Object> copy = new LinkedHashMap<>();
		headers.forEach((headerName, value) -> copy.put(
			requireText(headerName, description + " header names must not be blank"),
			Objects.requireNonNull(
				value,
				() -> description + " header value for '" + headerName + "' must not be null")));
		return Collections.unmodifiableMap(copy);
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String requireRouteName(String value) {
		String routeName = requireText(value, "routeName must not be blank");
		if (!ROUTE_NAME_PATTERN.matcher(routeName).matches()) {
			throw new IllegalArgumentException(
				"Route name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		}
		return routeName;
	}

	private static String requireNodeName(String value) {
		String nodeName = requireText(value, "node names must not be blank");
		if (!NODE_NAME_PATTERN.matcher(nodeName).matches()) {
			throw new IllegalArgumentException(
				"Node name must start with a letter and contain only letters, numbers, underscores, or hyphens");
		}
		return nodeName;
	}
}
