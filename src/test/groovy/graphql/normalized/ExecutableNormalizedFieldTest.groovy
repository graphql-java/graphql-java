package graphql.normalized

import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.language.Document
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class ExecutableNormalizedFieldTest extends Specification {

    def "can get children of object type"() {

        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            id: ID
            name : String!
        }
        type Cat implements Pet{
            id: ID
            name : String!
            meow : String
        }
        type Dog implements Pet{
            id: ID
            name : String!
            woof : String
        }
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                id
                name
                ... on Dog {
                    woof
                }
                ... on Cat {
                    meow
                }     
            }
        }
        
        """
        Document document = TestUtil.parseQuery(query)

        def normalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(graphQLSchema, document, null, CoercedVariables.emptyVariables())

        def pets = normalizedOperation.getTopLevelFields()[0]
        def allChildren = pets.getChildren()
        def dogFields = pets.getChildren("Dog")

        expect:
        allChildren.collect { it.name } == ["id", "name", "woof", "meow"]
        dogFields.collect { it.name } == ["id", "name", "woof"]
    }

}
