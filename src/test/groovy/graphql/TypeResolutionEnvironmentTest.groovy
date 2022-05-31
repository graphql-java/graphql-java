package graphql


import graphql.execution.TypeResolutionParameters
import graphql.language.Field
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import spock.lang.Specification

import static graphql.TestUtil.mergedField

class TypeResolutionEnvironmentTest extends Specification {

    def idl = """
        type Query {
            foo : Foo
            bar : Bar
            fooBar : FooBar
        }
        
        interface Foo {
            foo : String
        }
        
        interface Bar {
            bar : String
        }
        
        type FooBar implements Foo & Bar {
            foo : String
            bar : String
        }

        type FooImpl implements Foo {
            foo : String
        }
    """

    def schema = TestUtil.schema(idl)

    def interfaceType = schema.getType("Foo")

    def graphqlContext = GraphQLContext.newContext().of("a", "b").build()

    def "basic operations"() {
        given:

        def environment = TypeResolutionParameters.newParameters()
                .value("source")
                .argumentValues(() -> [a: "b"])
                .field(mergedField(new Field("field")))
                .fieldType(interfaceType)
                .schema(schema)
                .context("FooBar")
                .graphQLContext(graphqlContext)
                .localContext("LocalContext")
        .build()

        when:

        TypeResolver resolverOfFooBar = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                String source = env.getObject()
                assert source == "source"
                assert env.getField().getName() == "field"
                assert env.getFieldType() == interfaceType
                assert env.getContext() == "FooBar"
                assert env.getLocalContext() == "LocalContext"
                assert env.getGraphQLContext() == graphqlContext
                assert env.getArguments() == [a: "b"]
                return schema.getObjectType("FooBar")
            }
        }

        def objType = resolverOfFooBar.getType(environment)

        then:
        objType.name == "FooBar"


        when:

        TypeResolver resolverOfBadness = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                return schema.getObjectType("Foo")
            }
        }

        resolverOfBadness.getType(environment)

        then:
        thrown(GraphQLException)

    }

    def "using context"() {
        given:

        def resolverWithContext = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                String source = env.getObject()
                assert source == "source"
                assert env.getGraphQLContext().get("a") == "b"
                if ("FooBar" == env.getContext()) {
                    return schema.getObjectType("FooBar")
                }
                if ("Foo" == env.getContext()) {
                    return schema.getObjectType("FooImpl")
                }
                return null
            }
        }

        when:
        def environmentFooBar = TypeResolutionParameters.newParameters()
                .value("source")
                .argumentValues(() -> [:])
                .field(mergedField(new Field("field")))
                .fieldType(interfaceType)
                .schema(schema)
                .context("FooBar")
                .graphQLContext(graphqlContext)
                .build()

        def objTypeFooBar = resolverWithContext.getType(environmentFooBar)

        then:
        objTypeFooBar.name == "FooBar"

        when:
        def environmentFooImpl = TypeResolutionParameters.newParameters()
                .value("source")
                .argumentValues(() -> [:])
                .field(mergedField(new Field("field")))
                .fieldType(interfaceType)
                .schema(schema)
                .context("Foo")
                .graphQLContext(graphqlContext)
                .build()

        def objTypeFooImpl = resolverWithContext.getType(environmentFooImpl)

        then:
        objTypeFooImpl.name == "FooImpl"

    }
}
