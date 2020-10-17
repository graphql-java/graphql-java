package graphql

import graphql.introspection.Introspection
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLNamedInputType
import graphql.schema.GraphQLScalarType
import spock.lang.Specification


class Issue2055 extends Specification {

    def "issues 2055 for directive on SCALAR"() {
        given:
        def dsl = '''
            directive @testDirective(aDate: Date) on OBJECT | SCALAR
            
            directive @DummyDirective on SCALAR 
            
            scalar Date @DummyDirective  
            
            type Query {
                aQuery: String   
            }
        '''

        when:
        def schema = TestUtil.schema(dsl)

        then:
        schema.getType("Date") != null
        schema.getType("Date") instanceof GraphQLScalarType
        ((GraphQLScalarType) schema.getType("Date")).getDirective("DummyDirective") != null

        schema.getDirective("DummyDirective") != null
        schema.getDirective("DummyDirective").validLocations().size() == 1
        schema.getDirective("DummyDirective").validLocations().asList() == [Introspection.DirectiveLocation.SCALAR]

        schema.getDirective("testDirective") != null
        schema.getDirective("testDirective").validLocations().size() == 2
        schema.getDirective("testDirective").validLocations().asList() == [Introspection.DirectiveLocation.SCALAR, Introspection.DirectiveLocation.OBJECT]
        ((GraphQLNamedInputType) schema.getDirective("testDirective").getArgument("aDate").getType()).getName() == "Date"
    }


    def "issues 2055 for directive on ENUM"() {
        given:
        def dsl = '''
            directive @testDirective(anEnum: Episode = JEDI) on OBJECT | ENUM
            
            directive @DummyDirective on ENUM
            
            enum Episode @DummyDirective{
                JEDI
                NEWHOPE
            }
            
            type Query {
                aQuery: String   
            }
        '''
        when:
        def schema = TestUtil.schema(dsl)

        then:
        schema.getType("Episode") != null
        schema.getType("Episode") instanceof GraphQLEnumType
        ((GraphQLEnumType) schema.getType("Episode")).getValues().size() == 2
        ((GraphQLEnumType) schema.getType("Episode")).getDirective("DummyDirective").getName() == "DummyDirective"

        schema.getDirective("DummyDirective") != null
        schema.getDirective("DummyDirective").validLocations().size() == 1
        schema.getDirective("DummyDirective").validLocations().asList() == [Introspection.DirectiveLocation.ENUM]

        schema.getDirective("testDirective") != null
        schema.getDirective("testDirective").validLocations().size() == 2
        schema.getDirective("testDirective").validLocations().asList() == [Introspection.DirectiveLocation.OBJECT, Introspection.DirectiveLocation.ENUM]
        ((GraphQLNamedInputType) schema.getDirective("testDirective").getArgument("anEnum").getType()).getName() == "Episode"
    }


    def "issues 2055 for directive on ENUM_VALUE"() {
        given:
        def dsl = '''
            directive @testDirective(anEnum: Episode = JEDI) on OBJECT | ENUM_VALUE
            
            directive @DummyDirective on ENUM_VALUE
            
            enum Episode {
                JEDI  @DummyDirective
                NEWHOPE
            }
            
            type Query {
                aQuery: String   
            }
        '''
        when:
        def schema = TestUtil.schema(dsl)

        then:
        schema.getType("Episode") != null
        schema.getType("Episode") instanceof GraphQLEnumType
        ((GraphQLEnumType) schema.getType("Episode")).getValues().size() == 2
        ((GraphQLEnumType) schema.getType("Episode")).getValue("JEDI").getDirective("DummyDirective").getName() == "DummyDirective"

        schema.getDirective("DummyDirective") != null
        schema.getDirective("DummyDirective").validLocations().size() == 1
        schema.getDirective("DummyDirective").validLocations().asList() == [Introspection.DirectiveLocation.ENUM_VALUE]

        schema.getDirective("testDirective") != null
        schema.getDirective("testDirective").validLocations().size() == 2
        schema.getDirective("testDirective").validLocations().asList() == [Introspection.DirectiveLocation.OBJECT, Introspection.DirectiveLocation.ENUM_VALUE]
        ((GraphQLNamedInputType) schema.getDirective("testDirective").getArgument("anEnum").getType()).getName() == "Episode"
    }

}