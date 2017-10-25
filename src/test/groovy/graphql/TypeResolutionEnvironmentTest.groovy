package graphql

import graphql.language.Field
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import spock.lang.Specification

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
        
        type FooBar implements Foo, Bar {
            foo : String
            bar : String
        }

        type FooImpl implements Foo {
            foo : String
        }
    """

    def schema = TestUtil.schema(idl)

    def "basic operations"() {
        given:

        def environment = new TypeResolutionEnvironment("source", [:], new Field("field"), Scalars.GraphQLString, schema, "FooBar")

        when:

        TypeResolver resolverOfFooBar = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                String source = env.getObject()
                assert source == "source"
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
        def environmentFooBar = new TypeResolutionEnvironment("source", [:], new Field("field"), Scalars.GraphQLString, schema, "FooBar")
        def objTypeFooBar = resolverWithContext.getType(environmentFooBar)

        then:
        objTypeFooBar.name == "FooBar"

        when:
        def environmentFooImpl = new TypeResolutionEnvironment("source", [:], new Field("field"), Scalars.GraphQLString, schema, "Foo")
        def objTypeFooImpl = resolverWithContext.getType(environmentFooImpl)

        then:
        objTypeFooImpl.name == "FooImpl"

    }
}
