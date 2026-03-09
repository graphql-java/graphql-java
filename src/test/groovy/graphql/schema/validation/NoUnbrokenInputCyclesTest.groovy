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
import static graphql.schema.GraphQLList.list
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

    def "non-null list of non-null self-reference is not a cycle because empty list satisfies it"() {
        given:
        // input Example { self: [Example!]! value: String }
        GraphQLInputObjectType ExampleType = newInputObject()
                .name("Example")
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("self")
                        .type(typeRef("Example"))
                        .build())
                .field(GraphQLInputObjectField.newInputObjectField()
                        .name("value")
                        .type(GraphQLBoolean)
                        .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("test")
                .type(GraphQLBoolean)
                .argument(GraphQLArgument.newArgument()
                        .name("input")
                        .type(ExampleType))
                .build()

        // self: [Example!]!
        ExampleType.getFieldDefinition("self").replacedType = nonNull(list(nonNull(ExampleType)))
        def context = Mock(TraverserContext)
        context.getVarFromParents(SchemaValidationErrorCollector) >> errorCollector
        when:
        new NoUnbrokenInputCycles().visitGraphQLFieldDefinition(field, context)
        then:
        !errorCollector.containsValidationError(SchemaValidationErrorType.UnbrokenInputCycle)
    }
}
