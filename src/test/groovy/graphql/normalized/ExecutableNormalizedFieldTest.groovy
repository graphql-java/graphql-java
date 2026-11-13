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

    def "forEachFieldDefinition respects custom GraphqlFieldVisibility"() {
        // This test verifies that ExecutableNormalizedField.forEachFieldDefinition() uses
        // GraphqlFieldVisibility to look up fields. This is important for federated subgraphs
        // where the supergraph may have fields that don't exist in the local schema, but
        // a custom visibility can provide placeholder field definitions.
        String schema = """
        type Query{ 
            pet: Pet
        }
        type Pet {
            id: ID
            name: String
        }
        """
        
        // Create a custom visibility that provides a "virtual" field that doesn't exist on the type
        def customVisibility = new graphql.schema.visibility.GraphqlFieldVisibility() {
            @Override
            List<graphql.schema.GraphQLFieldDefinition> getFieldDefinitions(graphql.schema.GraphQLFieldsContainer fieldsContainer) {
                def fields = new ArrayList<>(fieldsContainer.getFieldDefinitions())
                // Add a virtual "age" field for Pet type
                if (fieldsContainer.name == "Pet") {
                    fields.add(graphql.schema.GraphQLFieldDefinition.newFieldDefinition()
                        .name("age")
                        .type(graphql.Scalars.GraphQLInt)
                        .build())
                }
                return fields
            }
            
            @Override
            graphql.schema.GraphQLFieldDefinition getFieldDefinition(graphql.schema.GraphQLFieldsContainer fieldsContainer, String fieldName) {
                // First check if the field exists on the type
                def field = fieldsContainer.getFieldDefinition(fieldName)
                if (field != null) {
                    return field
                }
                // Provide virtual "age" field for Pet type
                if (fieldsContainer.name == "Pet" && fieldName == "age") {
                    return graphql.schema.GraphQLFieldDefinition.newFieldDefinition()
                        .name("age")
                        .type(graphql.Scalars.GraphQLInt)
                        .build()
                }
                return null
            }
        }
        
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        
        // Rebuild schema with custom visibility
        def codeRegistry = graphql.schema.GraphQLCodeRegistry.newCodeRegistry(graphQLSchema.getCodeRegistry())
            .fieldVisibility(customVisibility)
            .build()
        graphQLSchema = graphQLSchema.transform { builder -> builder.codeRegistry(codeRegistry) }

        // Query that includes the "virtual" age field that exists only through visibility
        String query = """
        {
            pet {
                id
                name
                age
            }
        }
        """
        Document document = TestUtil.parseQuery(query)

        when:
        // This should succeed because the visibility provides the "age" field
        def normalizedOperation = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(
            graphQLSchema, document, null, CoercedVariables.emptyVariables())
        def pet = normalizedOperation.getTopLevelFields()[0]
        def fieldNames = pet.getChildren().collect { it.name }

        then:
        // The age field should be found via the custom visibility
        fieldNames.contains("age")
        fieldNames.containsAll(["id", "name", "age"])
    }

}
