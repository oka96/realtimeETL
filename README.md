# Realtime ETL

Spring Boot Java project that models realtime ETL routes with Spring Cloud Stream's functional binding style.

## Design

- `EtlRoute` is a builder-backed route definition.
- `EtlNode` is the customization point for each processing step.
- Custom nodes receive a non-null message and non-null `EtlContext`; the route wrapper rejects null inputs before invoking node code.
- `EtlContext` is a per-message scratchpad for sharing non-null route-scoped attributes between nodes, with typed required lookups for route transforms.
- `EtlRouteRegistry` indexes all route beans by route name.
- `RouteConfiguration` exposes each route as a Spring Cloud Stream `Function<Message<I>, Message<O>>`.
- `EtlRouteExecutionException` wraps node failures with the route and node names so production logs can identify the failed ETL step.
- `EtlHeaders` centralizes operational headers that every route adds to successful output messages.
- `EtlRouteDefinition` provides immutable route/node snapshots for diagnostics and tests.
- `EtlRouteCatalogLogger` logs configured route definitions and stream bindings at startup.
- `EtlRouteBindingValidator` fails startup when a registered route is not wired to Spring Cloud Stream bindings.
- `EtlFailureEvent` is a broker-published failure envelope for route errors.
- `EtlRoutesHealthIndicator` adds actuator health details for the configured route catalog.
- `RouteNames` centralizes the Java constants for the application route ids used by route builders, function lookups, and tests.

Current routes:

- `orderInvoice`: validates an `OrderCreated`, builds an `InvoiceEvent`, and tags route headers.
- `fraudReview`: validates an `OrderCreated`, calculates a risk score, builds a `FraudReviewEvent`, and tags route headers.
- `orderAnalytics`: validates an `OrderCreated`, classifies the amount band, builds an `OrderAnalyticsEvent`, and tags route headers.

Spring Cloud Stream binding names follow the functional convention:

- `orderInvoice-in-0` -> `orders.invoice.input`
- `orderInvoice-out-0` -> `invoices.output`
- `fraudReview-in-0` -> `orders.fraud.input`
- `fraudReview-out-0` -> `fraud-reviews.output`
- `orderAnalytics-in-0` -> `orders.analytics.input`
- `orderAnalytics-out-0` -> `order-analytics.output`
- `etlFailures-out-0` -> `etl.failures`

## Add A Route

Create an `EtlRoute` bean and expose it as a function:

```java
@Bean
EtlRoute myRoute() {
	return EtlRoute.builder("myRoute")
		.payloadTypes(MyInput.class, MyOutput.class)
		.requireInputHeaders("requireInputContract", Map.of(
			EtlHeaders.EVENT_TYPE, "my-input-event",
			EtlHeaders.EVENT_VERSION, "v1"))
		.node("customNode", new MyCustomNode())
		.transformPayload("mapPayload", MyInput.class, MyOutput.class, payload -> new MyOutput(payload))
		.enrichHeaders("markContract", Map.of(
			EtlHeaders.EVENT_TYPE, "my-output-event",
			EtlHeaders.EVENT_VERSION, "v1"))
		.build();
}

@Bean
Function<Message<MyInput>, Message<MyOutput>> myRoute(EtlRouteRegistry registry) {
	return registry.required("myRoute").toFunction(MyInput.class, MyOutput.class);
}
```

For production routes, add the route id to `RouteNames` first and use the constant from both the route builder and the registry lookup.
Then add the function name and bindings in `src/main/resources/application.yml`.
Keep `spring.cloud.function.definition` in the same order as the registered route catalog so startup logs, actuator details, and stream function configuration remain deterministic.
Production routes must declare non-blank string input contract headers for `etlEventType` and `etlEventVersion` so incorrect upstream events are rejected before route-specific business validation runs.
Production routes must also declare non-blank string output contract headers for `etlEventType` and `etlEventVersion` so downstream consumers can distinguish and version each route's output stream.
Use lowercase kebab-case event types such as `my-input-event`, and version tokens such as `v1`.

## Validation And Failure Handling

`OrderValidationNode` rejects malformed orders before transformation:

- missing `orderId`
- missing `customerId`
- missing or non-positive `amount`
- missing `currency`
- malformed `currency` values that are not three-letter codes

After validation, `OrderNormalizationNode` trims `orderId`, `customerId`, and `currency`, and uppercases `currency` before downstream scoring or mapping nodes run.

If a route rejects the declared input payload type or a node throws an exception, `EtlRoute` rethrows it as `EtlRouteExecutionException` with:

- `routeName()`
- `nodeName()`
- `traceId()`
- `sourceMessageId()`
- `sourceMessageTimestamp()`
- `completedNodeCount()`
- `nodeDurationsNanos()`
- original exception as the cause

That keeps node implementations simple while making failed stream processing easier to diagnose and correlate with message headers.
Route failure diagnostics require non-blank route/node names, non-negative source-message timestamps, non-negative completed-node counts, and non-negative node duration entries.
If a custom node throws `EtlRouteExecutionException` directly, the route boundary normalizes it with the active route name, current node name, generated or inbound trace id, source-message provenance, completed node count, and node durations before the failure reaches the stream error handler.
Input contract validation uses the same node failure path, so missing or wrong input event headers report the route, the input contract node, and the trace id.
Declared input payload type validation uses the same failure envelope before any route node runs, with node name `validateInputPayload` and completed node count `0`.
`EtlRouteErrorHandler` subscribes to Spring Integration's `errorChannel` and logs route failures with the route name, node name, trace id, source message id, source message timestamp, completed node count, and node durations.
It also publishes an `EtlFailureEvent` to the `etlFailures-out-0` binding (`etl.failures`) so operations or dead-letter consumers can handle rejected messages without scraping logs.
If the failure envelope cannot be built or the broker publish throws, the error handler records the failure-event publish metric with `result=error` and logs a warning instead of letting the error-channel handler fail.
The failure event includes route/node identity, trace, failure timestamp, input payload type, source-message provenance, completed node count, node durations, error type, and error message.
Failure messages are published as JSON with `etlEventType=etl-route-failure` and `etlEventVersion=v1` headers so downstream consumers can route the failure stream by contract before reading the payload.
Failure messages also carry `etlRoute`, `etlLastNode`, `etlNodeCount`, `etlTraceId`, `etlFailureTimestamp`, `etlInputPayloadType`, `etlSourceMessageId`, and `etlSourceMessageTimestamp` headers when those values are available; `etlFailureTimestamp` is required and matches the failure envelope payload.
The route, failed-node, and completed-node-count headers let broker-side consumers filter or alert on failure source without deserializing the failure payload.
Failure event route name, node name, and trace id must be non-blank so every failure can be grouped and correlated.
Failure event input payload type and source message id are optional, but must be non-blank when present.
Failure event error type and error message must also be non-blank; if the original exception has no message, the route failure summary is used as the published error message.
Failure event timing fields, including source-message timestamps, must be non-negative, and node duration entries must have non-blank node names with non-negative nanosecond durations.
The route integration tests also cover invalid input messages for every bundled route and assert that failed validation publishes an `EtlFailureEvent` without publishing downstream output events.

Route setup fails fast when configuration is ambiguous:

- route names must start with a letter and contain only letters, numbers, underscores, or hyphens
- at least one route must be registered at startup
- duplicate route names are rejected by `EtlRouteRegistry`
- route definitions require valid route names, payload type names, unique valid node names, and contract header names, plus non-null contract header values
- node names must start with a letter and contain only letters, numbers, underscores, or hyphens
- duplicate node names inside the same route are rejected by `EtlRoute`
- route registry lookups require non-blank route names
- missing route lookups fail with an explicit unknown-route message
- registered routes must match `spring.cloud.function.definition` exactly, with no missing, stale, or duplicate function names
- `spring.cloud.function.definition` must not contain blank entries from leading, trailing, or repeated semicolons
- `spring.cloud.function.definition` order must match the registered route order
- registered routes must declare concrete input and output payload types, not generic `Object`
- registered routes must declare non-blank string input contract headers for `etlEventType` and `etlEventVersion`
- registered routes must declare non-blank string output contract headers for `etlEventType` and `etlEventVersion`
- input and output contract header values must not contain surrounding whitespace
- input and output `etlEventType` values must use lowercase kebab-case
- input and output `etlEventVersion` values must match `v<digits>`
- registered routes must have input/output destinations and JSON content types
- binding destinations, content types, input consumer groups, and retry properties must not contain surrounding whitespace
- each route's input and output bindings must use different destinations to avoid feedback loops
- routes may share an input destination for fanout only when their consumer groups are different
- output destinations must be unique across registered routes to avoid mixing different payload contracts
- output `etlEventType` and `etlEventVersion` contract pairs must be unique across registered routes
- route output contracts cannot reuse the reserved ETL failure event contract
- the ETL failure event output binding must declare a unique JSON destination
- the ETL failure event destination must not match any route input destination
- input bindings must declare a durable consumer `group`
- binding content types must parse as `application/json`; media parameters such as `charset=UTF-8` are allowed
- input bindings must configure `max-attempts` of at least `2` so retries are actually enabled, plus positive backoff intervals with max backoff greater than or equal to initial backoff
- declared route input and output payload types are enforced during execution, with input type mismatches reported through the route failure path
- typed transform nodes validate their input and output payload types at the node boundary
- bundled order nodes reject null messages, null contexts, and non-`OrderCreated` payloads with explicit errors
- header enrichment nodes reject null header values
- payload helper methods reject null messages, transformers, and replacement payloads with explicit errors
- named node wrappers reject null messages and contexts before invoking custom node code
- primitive payload type declarations are rejected because stream payloads are reference objects
- context attribute keys must be non-blank and values must be non-null
- required context lookups fail with route/key/type details when a node dependency is missing or has the wrong type
- startup catalog, actuator, binding-validation, and error-handler components reject missing collaborators at construction
- actuator catalog rendering rejects null route definitions and null info builders with explicit errors

## Operational Headers

Every successful route output includes these headers:

- `etlRoute`: route name
- `etlInputPayloadType`: runtime Java class name of the input payload accepted by the route
- `etlOutputPayloadType`: runtime Java class name of the output payload produced by the route
- `etlLastNode`: last node that processed the message
- `etlNodeCount`: number of nodes that processed the message
- `etlNodeDurationsNanos`: immutable map of node name to elapsed nanoseconds for each node
- `etlDurationNanos`: route execution duration in nanoseconds
- `etlTraceId`: trimmed existing trace id from the input message, or a generated UUID when missing or blank
- `etlSourceMessageId`: the inbound Spring message id copied before the route publishes a new output message
- `etlSourceMessageTimestamp`: the inbound Spring message timestamp copied before the route publishes a new output message

The bundled routes require inbound messages to carry `etlEventType=order-created` and `etlEventVersion=v1`.
The bundled example routes also set `etlEventType` through `EtlHeaders.EVENT_TYPE` and `RouteEventTypes` so downstream consumers can distinguish invoice, fraud review, and analytics events by header.
They also set `etlEventVersion` through `EtlHeaders.EVENT_VERSION` and `RouteEventVersions` so downstream consumers can bind to a versioned output contract.
Route-managed operational headers cannot be set through `EtlRoute.Builder.enrichHeader` or `EtlRoute.Builder.enrichHeaders`; use those helpers for route-specific headers such as `etlEventType` and `etlEventVersion`.
This includes provenance headers such as `etlSourceMessageId` and `etlSourceMessageTimestamp`, which are always derived from the inbound message.
When a route advertises output contract headers through static `enrichHeaders`, `EtlRoute` verifies the final output message still contains those exact header values before returning the message.
This catches custom nodes that accidentally remove or overwrite the advertised event contract.
Static contract headers may be declared more than once only when the value matches; conflicting static declarations fail during route construction.

Route nodes expose shared context and classification constants such as `RiskScoreNode.CONTEXT_KEY`, `AmountBandNode.CONTEXT_KEY`, and the `AmountBandNode.SMALL`/`MEDIUM`/`LARGE` values to avoid drift between node implementations, route transforms, and tests.

Input bindings also configure a durable consumer `group` and bounded retry defaults in `src/main/resources/application.yml`.
`max-attempts` must be at least `2`; Spring Cloud Stream treats `1` as the initial delivery only, which disables retry behavior.

## Health And Readiness

The project includes Spring Boot Actuator health support without adding a UI or database dependency.
`EtlRoutesHealthIndicator` contributes an `etlRoutes` health component with the configured route count, the failure-event output binding, payload type, and contract headers, plus each route's input/output bindings, payload contract, input and output contract headers, node count, and ordered node list.
Input binding details include the durable consumer group and retry policy (`maxAttempts`, `backOffInitialInterval`, and `backOffMaxInterval`).

`src/main/resources/application.yml` exposes the HTTP actuator `health`, `info`, `metrics`, and `prometheus` endpoints, enables health probes, and includes `etlRoutes` in the readiness health group.
Deployment platforms can probe `/actuator/health/readiness` and fail readiness when route registration or binding validation fails during startup.
The `/actuator/info` endpoint includes an `etl` section with route count, failure-event binding details, payload type, and contract headers, input/output binding names, destinations, content types, input consumer groups, input retry policy, payload contracts, input and output contract headers such as `etlEventType` and `etlEventVersion`, node count, and ordered node names.

## Route Metrics

Each Spring Cloud Stream route function is wrapped with Micrometer instrumentation:

- `realtime.etl.route.messages`: counter tagged with `route` and `result`
- `realtime.etl.route.duration`: timer tagged with `route` and `result`
- `realtime.etl.routes.configured`: gauge with the number of registered ETL routes
- `realtime.etl.route.nodes`: gauge tagged with `route` for the configured node count of each route
- `realtime.etl.failure.events`: counter tagged with `route`, `node`, and publish `result`

The route metric `result` tag is either `success` or `failure`.
The failure-event publish `result` tag is `accepted`, `rejected`, or `error`.
Metric helpers validate required meter registries and non-blank route/result tags before registering meters, so instrumentation misconfiguration fails fast.
Route catalog metrics also require the route registry before binding gauges.
Failure-event metrics also require a route failure before recording.
These metrics are available through the actuator metrics endpoint, for example `/actuator/metrics/realtime.etl.route.messages` and `/actuator/metrics/realtime.etl.routes.configured`.
The same meters are also exposed in Prometheus format at `/actuator/prometheus` through Micrometer's Prometheus registry, using metric names such as `realtime_etl_route_messages_total` and `realtime_etl_failure_events_total`.
Validation failures and other route exceptions are counted with `result=failure`, and the integration tests verify that rejected stream messages do not publish downstream events while still incrementing the failure counter.

## Invoice Policy

The invoice route's output policy is configured under `realtime-etl.invoice`:

- `invoice-id-prefix`
- `status`

These properties are bound to `InvoiceRouteProperties` and validated at startup, so invoice ids and downstream status values can be tuned without changing Java code.
The policy record also validates direct Java construction, which keeps tests and custom route configuration code aligned with startup binding rules.
Values are trimmed, and blank values fail application startup.
`invoice-id-prefix` must be an uppercase alphanumeric token ending in `-`, such as `INV-` or `BILL-`, so generated invoice ids are easy to route and scan.
`status` must be an uppercase token such as `READY`, `PENDING`, or `MANUAL_REVIEW` so downstream consumers receive a stable status contract.

## Fraud Risk Policy

The fraud route's risk policy is configured under `realtime-etl.fraud-risk`:

- `base-score`
- `high-amount-threshold`
- `high-amount-score`
- `domestic-currency`
- `foreign-currency-score`
- `manual-review-threshold`

These properties are bound to `FraudRiskProperties` and validated at startup, so scoring can be tuned without changing Java code.
The policy record also validates direct Java construction, and `RiskScoreNode` requires a non-null policy.
Invalid values such as a blank or malformed domestic currency, or a non-positive high amount threshold, fail application startup.
`domestic-currency` is normalized to an uppercase three-letter code.
`manual-review-threshold` must be less than or equal to the maximum reachable risk score (`base-score + high-amount-score + foreign-currency-score`) so manual review cannot be disabled accidentally by an unreachable threshold.

## Analytics Band Policy

The analytics route's amount band policy is configured under `realtime-etl.analytics-band`:

- `medium-threshold`
- `large-threshold`

`AmountBandNode` classifies orders below the medium threshold as `SMALL`, orders below the large threshold as `MEDIUM`, and the rest as `LARGE`.
These properties are bound to `AnalyticsBandProperties` and validated at startup.
The policy record also validates direct Java construction so custom route tests cannot bypass threshold rules.
Thresholds must be positive, and the large threshold must be greater than the medium threshold.

## Route Catalog

`EtlRouteRegistry.definitions()` returns immutable `EtlRouteDefinition` snapshots containing:

- route name
- input binding name
- output binding name
- input payload type
- output payload type
- input contract headers
- output contract headers
- ordered node names
- node count

This gives logs, tests, or future operational surfaces a stable route catalog without exposing mutable route internals.
Actuator health and info route catalog details are also rendered as immutable top-level and nested maps so consumers cannot accidentally mutate diagnostic snapshots after they are built.

At startup, `EtlRouteCatalogLogger` writes the configured route count plus each route's payload contract, input and output contract headers, ordered node list, input binding, input destination, consumer group, retry policy, output binding, output destination, and the failure-event output destination to the application log.

## Example Payloads

Example payload files live under `src/test/resources/examples`:

- `etl-failure-event.json`
- `order-invoice-input.json`
- `order-invoice-output.json`
- `order-invoice-normalized-input.json`
- `order-invoice-normalized-output.json`
- `order-invoice-invalid-input.json`
- `order-invoice-invalid-currency-input.json`
- `fraud-review-input.json`
- `fraud-review-output.json`
- `fraud-review-normalized-input.json`
- `fraud-review-normalized-output.json`
- `fraud-review-invalid-input.json`
- `fraud-review-invalid-currency-input.json`
- `order-analytics-input.json`
- `order-analytics-output.json`
- `order-analytics-normalized-input.json`
- `order-analytics-normalized-output.json`
- `order-analytics-invalid-input.json`
- `order-analytics-invalid-currency-input.json`

The route integration tests read these files directly for both successful transformations and invalid-input rejection, and the test suite verifies that this README list matches the example directory.

## Test

```bash
mvn test
```

Integration tests use the Spring Cloud Stream test binder, so no external broker is required for test execution.
The test suite also verifies that every registered route has matching function and stream binding configuration, concrete payload types, input/output contract headers, and a configured failure-event output binding.
