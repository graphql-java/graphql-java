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
    """

    def schema = TestUtil.schema(idl)

    def "basic operations"() {
        given:

        def environment = new TypeResolutionEnvironment("source", [:], new Field("field"), Scalars.GraphQLString, schema)

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
}
