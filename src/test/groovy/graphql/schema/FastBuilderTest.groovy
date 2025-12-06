package graphql.schema

import graphql.AssertException
import graphql.Scalars
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar

class FastBuilderTest extends Specification {

    def "scalar type schema matches standard builder"() {
        given: "a custom scalar type"
        def customScalar = newScalar()
                .name("CustomScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type using the scalar"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(customScalar))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building with FastBuilder"
        def fastSchema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(customScalar)
                .build()

        and: "building with standard Builder"
        def standardSchema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry.build())
                .additionalType(customScalar)
                .build()

        then: "schemas are equivalent"
        fastSchema.queryType.name == standardSchema.queryType.name
        fastSchema.getType("CustomScalar") != null
        fastSchema.getType("CustomScalar").name == standardSchema.getType("CustomScalar").name
        // Check that the types in both schemas match (excluding system types added differently)
        fastSchema.typeMap.keySet().containsAll(["Query", "CustomScalar"])
        standardSchema.typeMap.keySet().containsAll(["Query", "CustomScalar"])
    }

    def "duplicate type with different instance throws error"() {
        given: "two different scalar instances with same name"
        def scalar1 = newScalar()
                .name("Duplicate")
                .coercing(GraphQLString.getCoercing())
                .build()
        def scalar2 = newScalar()
                .name("Duplicate")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding both scalars"
        new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(scalar1)
                .additionalType(scalar2)
                .build()

        then: "error is thrown"
        thrown(AssertException)
    }

    def "same type instance can be added multiple times"() {
        given: "a scalar type"
        def scalar = newScalar()
                .name("MyScalar")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(scalar))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding same scalar twice"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(scalar)
                .additionalType(scalar)
                .build()

        then: "no error and scalar is in schema"
        schema.getType("MyScalar") != null
    }

    def "null type is safely ignored"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding null type"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalType(null)
                .build()

        then: "no error"
        schema.queryType.name == "Query"
    }

    def "built-in directives are added automatically"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building schema"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .build()

        then: "built-in directives are present"
        schema.getDirective("skip") != null
        schema.getDirective("include") != null
        schema.getDirective("deprecated") != null
        schema.getDirective("specifiedBy") != null
        schema.getDirective("oneOf") != null
        schema.getDirective("defer") != null
    }

    def "query type is required"() {
        when: "creating FastBuilder with null query type"
        new GraphQLSchema.FastBuilder(GraphQLCodeRegistry.newCodeRegistry(), null, null, null)

        then: "error is thrown"
        thrown(AssertException)
    }

    def "code registry builder is required"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        when: "creating FastBuilder with null code registry"
        new GraphQLSchema.FastBuilder(null, queryType, null, null)

        then: "error is thrown"
        thrown(AssertException)
    }

    def "mutation and subscription types are optional"() {
        given: "query, mutation, and subscription types"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        def mutationType = newObject()
                .name("Mutation")
                .field(newFieldDefinition()
                        .name("setValue")
                        .type(GraphQLString))
                .build()

        def subscriptionType = newObject()
                .name("Subscription")
                .field(newFieldDefinition()
                        .name("valueChanged")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building schema with all root types"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, mutationType, subscriptionType)
                .build()

        then: "all root types are present"
        schema.queryType.name == "Query"
        schema.mutationType.name == "Mutation"
        schema.subscriptionType.name == "Subscription"
        schema.isSupportingMutations()
        schema.isSupportingSubscriptions()
    }

    def "schema description can be set"() {
        given: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "building schema with description"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .description("Test schema description")
                .build()

        then: "description is set"
        schema.description == "Test schema description"
    }

    def "additionalTypes accepts collection"() {
        given: "multiple scalar types"
        def scalar1 = newScalar()
                .name("Scalar1")
                .coercing(GraphQLString.getCoercing())
                .build()
        def scalar2 = newScalar()
                .name("Scalar2")
                .coercing(GraphQLString.getCoercing())
                .build()

        and: "a query type"
        def queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(GraphQLString))
                .build()

        and: "code registry"
        def codeRegistry = GraphQLCodeRegistry.newCodeRegistry()

        when: "adding types as collection"
        def schema = new GraphQLSchema.FastBuilder(codeRegistry, queryType, null, null)
                .additionalTypes([scalar1, scalar2])
                .build()

        then: "both types are in schema"
        schema.getType("Scalar1") != null
        schema.getType("Scalar2") != null
    }
}
