package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject

class GraphQLObjectTypeTest extends Specification {

    def "duplicate field definition fails"() {
        when:
        // preserve old constructor behavior test
        new GraphQLObjectType("TestObjectType", "description",
                [
                        newFieldDefinition().name("NAME").type(GraphQLString).build(),
                        newFieldDefinition().name("NAME").type(GraphQLString).build()
                ], [])
        then:
        thrown(AssertException)
    }

    def "duplicate field definition overwrites existing value"() {
        when:
        def objectType = newObject().name("TestObjectType")
                .field(newFieldDefinition().name("NAME").type(GraphQLString))
                .field(newFieldDefinition().name("NAME").type(GraphQLInt))
                .build()
        then:
        objectType.getName() == "TestObjectType"
        objectType.getFieldDefinition("NAME").getType() == GraphQLInt
    }

    def "builder can change existing object into a new one"() {
        given:
        def objectType = newObject().name("StartObjectType")
                .description("StartingDescription")
                .field(newFieldDefinition().name("Str").type(GraphQLString))
                .field(newFieldDefinition().name("Int").type(GraphQLInt))
                .build()

        when:
        def objectType2 = objectType.transform({ builder ->
            builder
                    .name("NewObjectName")
                    .description("NewDescription")
                    .field(newFieldDefinition().name("AddedInt").type(GraphQLInt)) // add more
                    .field(newFieldDefinition().name("Int").type(GraphQLInt)) // override and change
                    .field(newFieldDefinition().name("Str").type(GraphQLBoolean)) // override and change
        })
        then:

        objectType.getName() == "StartObjectType"
        objectType.getDescription() == "StartingDescription"
        objectType.getFieldDefinitions().size() == 2
        objectType.getFieldDefinition("Int").getType() == GraphQLInt
        objectType.getFieldDefinition("Str").getType() == GraphQLString

        objectType2.getName() == "NewObjectName"
        objectType2.getDescription() == "NewDescription"
        objectType2.getFieldDefinitions().size() == 3
        objectType2.getFieldDefinition("AddedInt").getType() == GraphQLInt
        objectType2.getFieldDefinition("Int").getType() == GraphQLInt
        objectType2.getFieldDefinition("Str").getType() == GraphQLBoolean
    }
}
