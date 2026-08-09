package graphql.arbitrary.graphqljava

import graphql.ExecutionInput
import graphql.arbitrary.Config
import graphql.arbitrary.GenInterfaceStubsIfNeeded
import graphql.arbitrary.MaxSelectionSetDepth
import graphql.arbitrary.NullNonNullableWeight
import graphql.arbitrary.ResolverExceptionWeight
import graphql.arbitrary.SchemaSize
import graphql.arbitrary.graphQLExecutionInput
import graphql.arbitrary.graphQLSchema
import graphql.schema.GraphQLSchema
import io.kotest.property.Arb
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map

internal val graphQLJavaPropertyConfig = Config(
    SchemaSize(15),
    GenInterfaceStubsIfNeeded(true),
    MaxSelectionSetDepth(5),
    NullNonNullableWeight(0.0),
    ResolverExceptionWeight(0.0)
)

internal fun Arb.Companion.graphQLJavaExecutionCase(): Arb<Pair<GraphQLSchema, ExecutionInput>> =
    graphQLSchema(graphQLJavaPropertyConfig).flatMap { schema ->
        graphQLExecutionInput(schema, graphQLJavaPropertyConfig).map { input ->
            schema to input
        }
    }
