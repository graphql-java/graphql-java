package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLUnionType.newUnionType

class GraphQLUnionTypeTest extends Specification {

    def "no possible types in union fails"() {
        when:
        newUnionType()
                .name("TestUnionType")
                .typeResolver(new TypeResolverProxy())
                .build()
        then:
        thrown(AssertException)
    }

    def objType1 = newObject().name("T1")
            .field(newFieldDefinition().name("f1").type(GraphQLBoolean))
            .build()
    def objType2 = newObject().name("T2")
            .field(newFieldDefinition().name("f1").type(GraphQLBoolean))
            .build()

    def objType3 = newObject().name("T3")
            .field(newFieldDefinition().name("f2").type(GraphQLBoolean))
            .build()

    def "object transformation works as expected"() {

        given:
        def startingUnion = newUnionType().name("StartingType")
                .description("StartingDescription")
                .possibleType(objType1)
                .possibleType(objType2)
                .typeResolver(new TypeResolverProxy())
                .build()

        when:
        def transformedUnion = startingUnion.transform({ builder ->
            builder
                    .name("NewName")
                    .description("NewDescription")
                    .clearPossibleTypes()
                    .possibleType(objType3)
        })
        then:

        startingUnion.getName() == "StartingType"
        startingUnion.getDescription() == "StartingDescription"
        startingUnion.getTypes().size() == 2

        transformedUnion.getName() == "NewName"
        transformedUnion.getDescription() == "NewDescription"
        transformedUnion.getTypes().size() == 1
    }

}
