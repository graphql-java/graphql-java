package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface

class GraphQLInterfaceTypeTest extends Specification {

    def "duplicate field definition fails"() {
        when:
        // preserve old constructor behavior test
        new GraphQLInterfaceType("TestInputObjectType", "description",
                [
                        newFieldDefinition().name("NAME").type(GraphQLString).build(),
                        newFieldDefinition().name("NAME").type(GraphQLString).build()
                ], new TypeResolverProxy())
        then:
        thrown(AssertException)
    }

    def "builder can change existing object into a new one"() {
        given:
        def startingInterface = newInterface().name("StartingType")
                .description("StartingDescription")
                .field(newFieldDefinition().name("Str").type(GraphQLString))
                .field(newFieldDefinition().name("Int").type(GraphQLInt))
                .typeResolver(new TypeResolverProxy())
                .build()

        when:
        def objectType2 = startingInterface.transform({ builder ->
            builder
                    .name("NewName")
                    .description("NewDescription")
                    .field(newFieldDefinition().name("AddedInt").type(GraphQLInt)) // add more
                    .field(newFieldDefinition().name("Int").type(GraphQLInt)) // override and change
                    .field(newFieldDefinition().name("Str").type(GraphQLBoolean)) // override and change
        })
        then:

        startingInterface.getName() == "StartingType"
        startingInterface.getDescription() == "StartingDescription"
        startingInterface.getFieldDefinitions().size() == 2
        startingInterface.getFieldDefinition("Int").getType() == GraphQLInt
        startingInterface.getFieldDefinition("Str").getType() == GraphQLString

        objectType2.getName() == "NewName"
        objectType2.getDescription() == "NewDescription"
        objectType2.getFieldDefinitions().size() == 3
        objectType2.getFieldDefinition("AddedInt").getType() == GraphQLInt
        objectType2.getFieldDefinition("Int").getType() == GraphQLInt
        objectType2.getFieldDefinition("Str").getType() == GraphQLBoolean
    }

}
