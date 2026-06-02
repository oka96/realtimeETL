package com.example.realtimeetl.routes;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.example.realtimeetl.etl.EtlFailureEvent;
import com.example.realtimeetl.order.OrderCreated;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class ExamplePayloadDocumentationTest {

	private static final Path EXAMPLES_DIRECTORY = Path.of("src/test/resources/examples");
	private static final Pattern EXAMPLE_BULLET_PATTERN = Pattern.compile("- `([^`]+\\.json)`");
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void readmeListsEveryExamplePayloadFile() throws Exception {
		List<String> documentedExamples = documentedExamples();
		List<String> actualExamples = actualExamples();

		assertThat(documentedExamples)
			.containsExactlyInAnyOrderElementsOf(actualExamples);
	}

	@Test
	void failureEventExampleDocumentsPublishedFailureEnvelope() throws Exception {
		EtlFailureEvent failure = objectMapper.readValue(
			EXAMPLES_DIRECTORY.resolve("etl-failure-event.json").toFile(),
			EtlFailureEvent.class);

		assertThat(failure.routeName()).isEqualTo(RouteNames.ORDER_INVOICE);
		assertThat(failure.nodeName()).isEqualTo("validateOrder");
		assertThat(failure.traceId()).isEqualTo("invoice-invalid-trace-1");
		assertThat(failure.failureTimestamp()).isEqualTo(1_780_127_000_500L);
		assertThat(failure.inputPayloadType()).isEqualTo(OrderCreated.class.getName());
		assertThat(failure.sourceMessageId()).isEqualTo("source-message-123");
		assertThat(failure.sourceMessageTimestamp()).isEqualTo(1_780_127_000_000L);
		assertThat(failure.completedNodeCount()).isEqualTo(1);
		assertThat(failure.nodeDurationsNanos())
			.containsOnlyKeys("requireOrderContract", "validateOrder");
		assertThat(failure.errorType()).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(failure.errorMessage()).isEqualTo("Order id is required");
		assertThat(EtlFailureEvent.EVENT_TYPE).isEqualTo("etl-route-failure");
		assertThat(EtlFailureEvent.EVENT_VERSION).isEqualTo(RouteEventVersions.V1);
	}

	private static List<String> documentedExamples() throws Exception {
		List<String> lines = Files.readAllLines(Path.of("README.md"));
		int sectionStart = lines.indexOf("## Example Payloads");
		int sectionEnd = lines.subList(sectionStart + 1, lines.size()).indexOf("## Test") + sectionStart + 1;

		assertThat(sectionStart).isNotNegative();
		assertThat(sectionEnd).isGreaterThan(sectionStart);

		return lines.subList(sectionStart, sectionEnd).stream()
			.flatMap(line -> EXAMPLE_BULLET_PATTERN.matcher(line).results())
			.map(MatchResult::group)
			.map(match -> match.substring(3, match.length() - 1))
			.toList();
	}

	private static List<String> actualExamples() throws Exception {
		try (Stream<Path> paths = Files.list(EXAMPLES_DIRECTORY)) {
			return paths
				.filter(path -> path.getFileName().toString().endsWith(".json"))
				.map(path -> path.getFileName().toString())
				.toList();
		}
	}
}
