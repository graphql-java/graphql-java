package graphql.schema.validation

import graphql.Scalars
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLUnionType
import spock.lang.Specification

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition

class CodeRegistryIsValidTest extends Specification {

    def sdl = '''
            type Query {
              pizza(name: String!) : PizzaInterface
              unionOfPizza : PizzaUnion
            }
            
            interface PizzaInterface {
                name : String
                size : Int
            }
            
            union PizzaUnion = MeatLovers | Seafood
            
            type MeatLovers implements PizzaInterface {
                name : String
                size : Int
            }

            type Seafood implements PizzaInterface {
                name : String
                size : Int
            }
            
        '''

    def "missing type resolvers are detected"() {


        when:

        def pizzaInterface = GraphQLInterfaceType.newInterface().name("Pizza")
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(newFieldDefinition().name("size").type(Scalars.GraphQLInt))
                .build()

        def meatLovers = GraphQLObjectType.newObject().name("MeatLovers").withInterface(pizzaInterface)
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(newFieldDefinition().name("size").type(Scalars.GraphQLInt))
                .build()
        def seaFood = GraphQLObjectType.newObject().name("SeaFood").withInterface(pizzaInterface)
                .field(newFieldDefinition().name("name").type(Scalars.GraphQLString))
                .field(newFieldDefinition().name("size").type(Scalars.GraphQLInt))
                .build()

        def pizzaUnion = GraphQLUnionType.newUnionType().name("PizzaUnion").possibleType(meatLovers).possibleType(seaFood).build()


        def queryType = GraphQLObjectType.newObject().name("Query")
                .field(newFieldDefinition().name("pizza").type(pizzaInterface))
                .field(newFieldDefinition().name("pizzaUnion").type(pizzaUnion))
                .build()

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry().build()

        GraphQLSchema graphQLSchema = GraphQLSchema.newSchema()
                .query(queryType)
                .codeRegistry(codeRegistry)
                .build()

        then:
        def schemaProblem = thrown(InvalidSchemaException)
        schemaProblem.getErrors().size() == 1
        schemaProblem.getErrors().first().description == "Required input field 'PizzaInfo.pineapples' cannot be deprecated."
    }
}
