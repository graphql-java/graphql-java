package graphql.schema.validation

import graphql.schema.*
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectType.newInputObject

class NoUnbrokenInputCyclesTest extends Specification {

    SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

    def "infinitely recursive input type results in error"() {
        given:
        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("friend")
                .type(new GraphQLTypeReference("Person"))
                .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("exists")
                .type(GraphQLBoolean)
                .argument(GraphQLArgument.newArgument()
                .name("person")
                .type(PersonInputType))
                .build()

        PersonInputType.getFieldDefinition("friend").type = new GraphQLNonNull(PersonInputType)
        when:
        new NoUnbrokenInputCycles().check(field, errorCollector)
        then:
        errorCollector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
    }
}
