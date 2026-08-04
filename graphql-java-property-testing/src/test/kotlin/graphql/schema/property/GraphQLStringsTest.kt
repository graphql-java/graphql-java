@file:Suppress("ForbiddenImport")

package graphql.schema.property

import graphql.Scalars
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import io.kotest.property.Arb
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class GraphQLStringsTest : KotestPropertyBase() {
    @Test
    fun `Arb_graphQLName produces spec-compliant names`(): Unit =
        runBlocking {
            val patt = Regex("^[a-z,A-Z,_][a-z,A-Z,_,0-9,]*")
            Arb.graphQLName().forAll {
                it.matches(patt)
            }
        }

    @Test
    fun `Arb_graphQLName produces names that are valid GraphQL type names`(): Unit =
        runBlocking {
            val placeholder =
                GraphQLFieldDefinition
                    .newFieldDefinition()
                    .name("placeholder")
                    .type(Scalars.GraphQLInt)
                    .build()

            Arb.graphQLName().forAll { typeName ->
                val query =
                    GraphQLObjectType
                        .newObject()
                        .name(typeName)
                        .field(placeholder)
                        .build()
                val result =
                    Result.runCatching {
                        GraphQLSchema.newSchema().query(query).build()
                    }

                result.isSuccess
            }
        }

    @Test
    fun `Arb_graphQLName does not produce introspection names`(): Unit =
        runBlocking {
            Arb.graphQLName(1..4).forNone { it.startsWith("__") }
        }

    @Test
    fun `Arb_graphQLFieldName produces names that are valid GraphQL field names`(): Unit =
        runBlocking {
            val query =
                GraphQLObjectType
                    .newObject()
                    .name("Query")
                    .build()

            Arb.graphQLFieldName().forAll { fieldName ->
                val newQuery =
                    query.transform {
                        val field =
                            GraphQLFieldDefinition
                                .newFieldDefinition()
                                .name(fieldName)
                                .type(Scalars.GraphQLInt)
                                .build()
                        it.clearFields().field(field)
                    }

                val result =
                    Result.runCatching {
                        GraphQLSchema.newSchema().query(newQuery).build()
                    }

                result.isSuccess
            }
        }

    @Test
    fun `Arb_graphQLFieldName does not produce introspection names`(): Unit =
        runBlocking {
            Arb.graphQLFieldName().forNone { it.startsWith("__") }
        }

    @Test
    fun `Arb_graphQLEnumValueName does not produce invalid names`(): Unit =
        runBlocking {
            Arb.graphQLEnumValueName().forNone {
                it.startsWith("__") ||
                    it == "true" ||
                    it == "false" ||
                    it == "null"
            }
        }

    @Test
    fun `BanFieldNames`(): Unit =
        runBlocking {
            // create a ban list of single-letter field names, a-m and A-M
            val banned = CharRange('a', 'm').fold(emptySet<String>()) { acc, ch ->
                acc + ch.toString() + ch.uppercase()
            }

            // create a set of single-char names
            val cfg = Config.default +
                (BanFieldNames to banned) +
                (FieldNameLength to 1..2) +
                (SchemaSize to 1)

            val arbs = listOf(
                Arb.graphQLFieldName(cfg),
                Arb.graphQLEnumValueName(cfg),
                Arb.graphQLArgumentName(cfg)
            )

            arbs.forEach { arb ->
                arb.forNone { banned.contains(it) }
            }
        }
}
