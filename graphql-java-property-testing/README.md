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

[kotest]: https://kotest.io/docs/proptest/property-based-testing.html
[viaduct]: https://github.com/airbnb/viaduct
[module]: https://github.com/airbnb/viaduct/tree/e91e45f1976d7311729af32d48fcd4aab8652516/core/shared/arbitrary
[commit]: https://github.com/airbnb/viaduct/commit/e91e45f1976d7311729af32d48fcd4aab8652516
