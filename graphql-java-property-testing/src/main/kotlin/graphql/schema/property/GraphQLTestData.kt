package graphql.schema.property

import graphql.Directives
import graphql.language.Directive
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.VariableDefinition
import graphql.language.VariableReference
import graphql.parser.Parser
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.idl.FastSchemaGenerator
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaParser
import graphql.schema.idl.SchemaPrinter
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.map

/** A generated schema, executable document, and matching external variable values. */
data class GraphQLTestData(
    val sdl: String,
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
)

/**
 * Generate complete GraphQL test data, optionally filtering it using [MinQueryLength] and
 * [MinVariablesCount]. The remaining schema and document configuration keys shape the data.
 */
fun Arb.Companion.graphQLTestData(config: Config = Config.default): Arb<GraphQLTestData> =
    graphQLSchema(config).flatMap { schema ->
        graphQLExecutionInput(schema, config)
            .filter { executionInput ->
                executionInput.query.length >= config[MinQueryLength] &&
                    executionInput.variables.size >= config[MinVariablesCount]
            }.map { executionInput ->
                GraphQLTestData(
                    sdl = SchemaPrinter().print(schema),
                    query = executionInput.query,
                    variables = executionInput.variables
                )
            }
    }

/** Summary counts for generated GraphQL test data. */
data class GraphQLTestDataStats(
    val schemaStats: Map<String, Int>,
    val queryStats: Map<String, Int>
) {
    /** Format the collected statistics as an indented human-readable report. */
    fun format(indent: String = "  "): String =
        formatMap("schema", schemaStats, indent) + formatMap("query", queryStats, indent)

    private fun formatMap(
        label: String,
        values: Map<String, Int>,
        indent: String
    ): String =
        buildString {
            append(label.prependIndent(indent))
            appendLine(":")
            values.forEach { (key, value) ->
                append(key.prependIndent(indent + indent))
                appendLine(": $value")
            }
        }

    companion object {
        /** Derive statistics from [data]. */
        operator fun invoke(data: GraphQLTestData): GraphQLTestDataStats {
            val schema = parseSchema(data.sdl)
            val document = Parser().parseDocument(data.query)
            return GraphQLTestDataStats(
                schemaStats = schemaStats(data, schema),
                queryStats = queryStats(data, document)
            )
        }

        private fun parseSchema(sdl: String): GraphQLSchema =
            FastSchemaGenerator().makeExecutableSchema(
                SchemaParser().parse(sdl),
                RuntimeWiring.MOCKED_WIRING
            )

        private fun schemaStats(
            data: GraphQLTestData,
            schema: GraphQLSchema
        ): Map<String, Int> {
            var objectTypes = 0
            var objectFields = 0
            var unionTypes = 0
            var listTypedFields = 0
            var nonNullFields = 0
            var interfaceTypes = 0
            var interfaceFields = 0
            var enumTypes = 0
            var concretizations = 0

            schema.typeMap.values.forEach { type ->
                when (type) {
                    is GraphQLObjectType -> {
                        objectTypes++
                        if (type.interfaces.isNotEmpty()) concretizations++
                        objectFields += type.fieldDefinitions.size
                        type.fieldDefinitions.forEach { field ->
                            var fieldType: GraphQLType = field.type
                            while (GraphQLTypeUtil.isWrapped(fieldType)) {
                                if (fieldType is GraphQLList) listTypedFields++
                                if (fieldType is GraphQLNonNull) nonNullFields++
                                fieldType = GraphQLTypeUtil.unwrapOne(fieldType)
                            }
                        }
                    }
                    is GraphQLUnionType -> {
                        unionTypes++
                        concretizations += type.types.size
                    }
                    is GraphQLEnumType -> enumTypes++
                    is GraphQLInterfaceType -> {
                        interfaceTypes++
                        interfaceFields += type.fieldDefinitions.size
                    }
                }
            }

            return linkedMapOf(
                "sdl length" to data.sdl.length,
                "type definitions" to schema.typeMap.size,
                "object definitions" to objectTypes,
                "object fields" to objectFields,
                "list-typed object fields" to listTypedFields,
                "non-nullable object fields" to nonNullFields,
                "union definitions" to unionTypes,
                "interface definitions" to interfaceTypes,
                "interface fields" to interfaceFields,
                "enum definitions" to enumTypes,
                "type concretizations" to concretizations
            )
        }

        private fun queryStats(
            data: GraphQLTestData,
            document: Document
        ): Map<String, Int> {
            var fieldSelections = 0
            var aliasedSelections = 0
            var argumentedSelections = 0
            var fragmentSpreads = 0
            var inlineFragments = 0
            var fragmentDefinitions = 0
            var variableReferences = 0
            var variableDefinitions = 0
            var skipIncludes = 0

            document.allChildren.forEach { node ->
                when (node) {
                    is Field -> {
                        fieldSelections++
                        if (node.alias != null) aliasedSelections++
                        if (node.arguments.isNotEmpty()) argumentedSelections++
                    }
                    is FragmentDefinition -> fragmentDefinitions++
                    is FragmentSpread -> fragmentSpreads++
                    is InlineFragment -> inlineFragments++
                    is VariableReference -> variableReferences++
                    is VariableDefinition -> variableDefinitions++
                    is Directive -> {
                        if (node.name in setOf(Directives.SkipDirective.name, Directives.IncludeDirective.name)) {
                            skipIncludes++
                        }
                    }
                }
            }

            return linkedMapOf(
                "query length" to data.query.length,
                "query lines" to data.query.lines().size,
                "field selections" to fieldSelections,
                "aliased selections" to aliasedSelections,
                "argumented selections" to argumentedSelections,
                "fragment definitions" to fragmentDefinitions,
                "fragment spreads" to fragmentSpreads,
                "inline fragments" to inlineFragments,
                "variable definitions" to variableDefinitions,
                "variable references" to variableReferences,
                "skips and includes" to skipIncludes,
                "external variables" to data.variables.size
            )
        }
    }
}
