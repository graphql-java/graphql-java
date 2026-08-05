# graphql-java property testing

This module provides [Kotest property-testing][kotest] `Arb` generators for
graphql-java schemas, types, documents, execution inputs, output values, and
related test data.

Use the same version as graphql-java:

```kotlin
testImplementation("com.graphql-java:graphql-java-property-testing:<version>")
```

For example:

```kotlin
checkAll(Arb.graphQLSchema()) { schema ->
    // Check a property of the generated GraphQLSchema.
}
```

The implementation is derived from the [Airbnb Viaduct repository][viaduct],
specifically its [`core/shared/arbitrary` module][module] at
[commit `e91e45f1976d7311729af32d48fcd4aab8652516`][commit], and is adapted to
generate graphql-java's native schema and language AST types.

The upstream source is licensed under Apache License 2.0. The license text is
included in the published artifact and is also available in this repository at
`additionallicenses/APACHE-LICENSE-2.0.txt`.

## Viaduct-only test exclusions

The upstream tests keep their original JUnit 5 structure and use Kotest for
property generation. The following cases are intentionally not copied because
executing them would require Viaduct runtime or schema classes:

- `GraphQLDocumentGenTest`: the `Arb.viaductSchema()` half of
  `Arb_graphQLDocument -- generates valid documents for arbitrary schemas`.
  The graphql-java schema half remains in the test.
- `GraphQLSchemasTest`: `Arb-viaductSchema can generate schemas` and
  `regression -- applied directives can be binary encoded without directive
  cycles`. The Viaduct binary-encoding assertion is also omitted from the three
  `AddAppliedDirectives prevents ...` tests; their directive-cycle assertions
  remain.
- `UtilTest`: `ViaductSchema objects` and `ViaductSchema objectCoordinates`.
- The complete `ArbIRResultTest`, `ArbIRTest`, and `IDValueGenTest` suites,
  which require Viaduct IR, global-ID, or mapping types.
- The complete `ArbitraryEngineSelectionSetImplTest`, `CheckersTest`,
  `EngineDataExerciserTest`, `FieldResolversTest`, `NodeResolversTest`,
  `RequiredSelectionSetGenTest`, `ResolverConfigTest`, `ResolverValueGenTest`,
  and `VariablesResolversTest` suites, which require Viaduct engine selection,
  resolver, or execution APIs. The native graphql-java value-generator
  replacements have their own tests in `GraphQLExternalInputValueGeneratorTest`
  and `GraphQLOutputValuesTest`.
- The complete `ViaductDescriptorTest`, `ViaductExecutionInputsTest`,
  `ViaductSchemasTest`, and `ViaductsTest` suites, which exercise Viaduct
  service, schema, bootstrap, or execution APIs.

The original manually-run `DeepArbSuite` seed-march/minimum-violation tests and
the `GraphQLSchemasTest` seed-march/schema-dump tests remain `@Disabled`, as they
are upstream; they are retained rather than excluded.

[kotest]: https://kotest.io/docs/proptest/property-based-testing.html
[viaduct]: https://github.com/airbnb/viaduct
[module]: https://github.com/airbnb/viaduct/tree/e91e45f1976d7311729af32d48fcd4aab8652516/core/shared/arbitrary
[commit]: https://github.com/airbnb/viaduct/commit/e91e45f1976d7311729af32d48fcd4aab8652516
