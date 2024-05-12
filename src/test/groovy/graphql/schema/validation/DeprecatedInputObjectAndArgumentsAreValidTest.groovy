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
              spicy: Boolean @deprecated(reason: "Don't need this nullable input field")
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 1
        schemaProblem.getErrors().first().description == "Required input field 'PizzaInfo.pineapples' cannot be deprecated."
    }

    def "multiple required input fields cannot be deprecated"() {
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
              spicy: Boolean! @deprecated(reason: "Don't need this input field")
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 2
        schemaProblem.getErrors()[0].description == "Required input field 'PizzaInfo.pineapples' cannot be deprecated."
        schemaProblem.getErrors()[1].description == "Required input field 'PizzaInfo.spicy' cannot be deprecated."
    }

    def "required input field list cannot be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(pizzaInfos: [PizzaInfo]!): String
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
        schemaProblem.getErrors().first().description == "Required input field 'PizzaInfo.pineapples' cannot be deprecated."
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

    def "required field argument cannot be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(name: String!, pineapples: Boolean! @deprecated(reason: "Don't need this field argument")): String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 1
        schemaProblem.getErrors().first().description == "Required argument 'pineapples' on field 'updatePizza' cannot be deprecated."
    }

    def "multiple required field arguments cannot be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(name: String! @deprecated(reason: "yeah nah"), pineapples: Boolean! @deprecated(reason: "Don't need this field argument")): String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 2
        schemaProblem.getErrors()[0].description == "Required argument 'name' on field 'updatePizza' cannot be deprecated."
        schemaProblem.getErrors()[1].description == "Required argument 'pineapples' on field 'updatePizza' cannot be deprecated."
    }

    def "nullable field argument can be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(name: String!, pineapples: Boolean @deprecated(reason: "Don't need this field argument")): String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }

    def "non-nullable field argument with default value can be deprecated"() {
        def sdl = '''
            type Query {
              pizza(name: String!): String
            }
            
            type Mutation {
              updatePizza(name: String!, pineapples: Boolean! = false @deprecated(reason: "Don't need this field argument")): String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }

    def "required directive argument cannot be deprecated"() {
        def sdl = '''
            directive @pizzaDirective(name: String!, likesPineapples: Boolean! @deprecated(reason: "Don't need this directive argument")) on FIELD_DEFINITION

            type Query {
              pizza(name: String!): String @pizzaDirective(name: "Stefano", likesPineapples: false)
            }

            type Mutation {
              updatePizza(name: String!, pineapples: Boolean!): String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 1
        schemaProblem.getErrors().first().description == "Required argument 'likesPineapples' on directive 'pizzaDirective' cannot be deprecated."
    }

    def "multiple required directive arguments cannot be deprecated"() {
        def sdl = '''
            directive @pizzaDirective(name: String! @deprecated, likesPineapples: Boolean! @deprecated(reason: "Don't need this directive argument")) on FIELD_DEFINITION

            type Query {
              pizza(name: String!): String @pizzaDirective(name: "Stefano", likesPineapples: false)
            }

            type Mutation {
              updatePizza(name: String!, pineapples: Boolean!): String
            }
        '''

        when:
        TestUtil.schema(sdl)

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 2
        schemaProblem.getErrors()[0].description == "Required argument 'name' on directive 'pizzaDirective' cannot be deprecated."
        schemaProblem.getErrors()[1].description == "Required argument 'likesPineapples' on directive 'pizzaDirective' cannot be deprecated."
    }

    def "nullable directive argument can be deprecated"() {
        def sdl = '''
            directive @pizzaDirective(name: String!, likesPineapples: Boolean @deprecated(reason: "Don't need this directive argument")) on FIELD_DEFINITION

            type Query {
              pizza(name: String!): String @pizzaDirective(name: "Stefano", likesPineapples: false)
            }

            type Mutation {
              updatePizza(name: String!, pineapples: Boolean!): String
            }

        '''

        when:
        TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }

    def "non-nullable directive argument with default value can be deprecated"() {
        def sdl = '''
            directive @pizzaDirective(name: String!, likesPineapples: Boolean! = false @deprecated(reason: "Don't need this directive argument")) on FIELD_DEFINITION

            type Query {
              pizza(name: String!): String @pizzaDirective(name: "Stefano", likesPineapples: false)
            }

            type Mutation {
              updatePizza(name: String!, pineapples: Boolean!): String
            }

        '''

        when:
        TestUtil.schema(sdl)

        then:
        noExceptionThrown()
    }

}
