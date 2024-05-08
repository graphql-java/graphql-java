package graphql.schema.validation

import graphql.TestUtil
import spock.lang.Specification

class DeprecatedInputObjectAndArgumentsAreValidTest extends Specification {

    def "required input field cannot be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(pizzaInfo: PizzaInfo!): String
            }
            
            input PizzaInfo {
              name: String!
              pineapples: Boolean! @deprecated(reason: "Don't need this input field")
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 1
        schemaProblem.getErrors().first().description == "Required input field PizzaInfo.pineapples cannot be deprecated."
    }

    def "nullable input field can be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(pizzaInfo: PizzaInfo!): String
            }
            
            input PizzaInfo {
              name: String!
              pineapples: Boolean @deprecated(reason: "Don't need this input field")
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }

    def "non-nullable input field with default value can be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(pizzaInfo: PizzaInfo!): String
            }
            
            input PizzaInfo {
              name: String!
              pineapples: Boolean! = false @deprecated(reason: "Don't need this input field")
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }

}
