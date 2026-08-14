# Arbitrary

This module generates streams of arbitrary GraphQL objects that are suitable for
use in fuzzing and property-based testing.

The generators produce graphql-java objects that can be used to test any GraphQL
system built on graphql-java.

The arbitrary object streams are exposed as a Kotest property
[Arb](https://kotest.io/docs/proptest/property-test-generators.html#arbitrary);
properties of an `Arb` can be tested using JUnit, Kotest, or other unit testing
frameworks.

## Dependency

Use the same version as graphql-java:

```kotlin
testImplementation("com.graphql-java:graphql-java-arbitrary:<version>")
```

Reusable test base classes such as `DeepArbSuite` and `ArbPropertyBase` are
published as Gradle test fixtures:

```kotlin
testImplementation(testFixtures("com.graphql-java:graphql-java-arbitrary:<version>"))
```

## Quick Start

```kotlin
import graphql.arbitrary.graphQLSchema
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import io.kotest.property.Arb
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class QuickStartTest {
    @Test
    fun `arbitrary schemas can be roundtripped through SDL`(): Unit = runBlocking {
        Arb.graphQLSchema().checkAll { schema ->
            val sdl = SchemaPrinter().print(schema)
            SchemaParser().parse(sdl)
        }
    }
}
```

## Configuration

All generator functions in this module accept an optional `Config` parameter.

This is used to customize the shape of objects that are produced, and is
primarily oriented around "weights" that control how often a GraphQL feature
will be explored by a generator.

Omitting a `Config` will use a default configuration that approximates a
real-world probability distribution. The generators in this module are tuned
to have acceptable performance for approximately 1000 generations when using
the default `Config`.

A `Config` may be modified to steer the distribution of generated objects
towards an area of interest. This library includes configuration knobs for
controlling the probabilities of a field definition taking arguments, a
resolver throwing an exception, an inline fragment omitting a type condition,
and more.

See [`Configs.kt`](src/main/kotlin/graphql/arbitrary/Configs.kt) for a full list
of configuration knobs.

### Example

```kotlin
import graphql.arbitrary.Config
import graphql.arbitrary.ObjectTypeSize
import graphql.arbitrary.SchemaSize
import graphql.arbitrary.graphQLSchema
import io.kotest.property.Arb

// Create a custom Config that generates wide object types.
val extraLargeObjectConfig = Config(
    SchemaSize(10),
    ObjectTypeSize(200..1000)
)

val arbSchema = Arb.graphQLSchema(extraLargeObjectConfig)
```

## Library

Useful generators and methods provided by this library.

### Schema and Documents

| Generator                   | Description                                                       |
|-----------------------------|-------------------------------------------------------------------|
| `Arb.graphQLSchema`         | Generate arbitrary `graphql.schema.GraphQLSchema` objects         |
| `Arb.graphQLDocument`       | Generate valid `graphql.language.Document` objects for a schema   |
| `Arb.graphQLExecutionInput` | Generate valid `graphql.ExecutionInput` objects for a schema      |
| `Arb.graphQLTypes`          | Generate compatible collections of graphql-java schema types      |
| `Arb.graphQLName`           | Generate names suitable for use in GraphQL                        |

### Values

| Generator                | Description                                                        |
|--------------------------|--------------------------------------------------------------------|
| `Arb.graphQLOutputValue` | Generate native result values for a type, selection, or operation  |

### Execution

| Generator          | Description                                                        |
|--------------------|--------------------------------------------------------------------|
| `arbGraphQL`       | Create an executable `GraphQL` backed by deterministic arbitrary data |
| `arbCodeRegistry`  | Create a `GraphQLCodeRegistry` that serves arbitrary data          |
| `arbRuntimeWiring` | Create a `RuntimeWiring` that serves arbitrary data for an SDL schema |

## History

This code was originally created by
[James Bellenger](https://github.com/jbellenger) at Airbnb as part of the
[Viaduct project](https://github.com/airbnb/viaduct).

It was adapted from Viaduct's
[`core/shared/arbitrary` module](https://github.com/airbnb/viaduct/tree/e91e45f1976d7311729af32d48fcd4aab8652516/core/shared/arbitrary)
to generate graphql-java's native schema and language AST types.

The original source is licensed under the Apache License 2.0. The license text
is included in the published artifact and is also available in this repository
at `additionallicenses/APACHE-LICENSE-2.0.txt`.
