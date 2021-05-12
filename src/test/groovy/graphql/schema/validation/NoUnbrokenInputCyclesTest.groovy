package graphql.schema.validation

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectField
import graphql.schema.GraphQLInputObjectType
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLTypeReference.typeRef

class NoUnbrokenInputCyclesTest extends Specification {

    SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()

    def "infinitely recursive input type results in error"() {
        given:
        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(GraphQLInputObjectField.newInputObjectField()
                .name("friend")
                .type(typeRef("Person"))
                .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("exists")
                .type(GraphQLBoolean)
                .argument(GraphQLArgument.newArgument()
                        .name("person")
                        .type(PersonInputType))
                .build()

        PersonInputType.getFieldDefinition("friend").replacedType = nonNull(PersonInputType)
        def context = Mock(TraverserContext)
        context.getVarFromParents(SchemaValidationErrorCollector) >> errorCollector
        when:
        new NoUnbrokenInputCycles().visitGraphQLFieldDefinition(field, context)
        then:
        errorCollector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
    }
}
