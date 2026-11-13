package graphql.schema.validation

import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef

class InputAndOutputTypesUsedAppropriatelyTest extends Specification {

    def "output type within input context is caught"() {
        given:

        GraphQLObjectType OutputType = newObject()
                .name("OutputType")
                .field(newFieldDefinition().name("field").type(GraphQLString))
                .build()

        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(newInputObjectField()
                        .name("friend")
                        .type(nonNull(list(nonNull(typeRef("OutputType")))))
                        .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("exists")
                .type(GraphQLBoolean)
                .argument(GraphQLArgument.newArgument()
                        .name("person")
                        .type(PersonInputType))
                .build()

        GraphQLObjectType queryType = newObject()
                .name("Query")
                .field(field)
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(queryType)
                .additionalTypes([OutputType] as Set)
                .build()
        then:
        def schemaException = thrown(InvalidSchemaException)
        def errors = schemaException.getErrors().collect { it.description }
        errors.contains("The output type 'OutputType' has been used in an input type context : 'Person.friend'")
    }

    def "input type within output context is caught"() {
        given:

        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(newInputObjectField()
                        .name("friend")
                        .type(GraphQLString)
                        .build())
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("outputField")
                .type(nonNull(list(nonNull(typeRef("Person")))))
                .build()

        GraphQLObjectType queryType = newObject()
                .name("Query")
                .field(field)
                .build()

        when:
        GraphQLSchema.newSchema()
                .query(queryType)
                .additionalTypes([PersonInputType] as Set)
                .build()
        then:
        def schemaException = thrown(InvalidSchemaException)
        def errors = schemaException.getErrors().collect { it.description }
        errors.contains("The input type 'Person' has been used in a output type context : 'Query.outputField'")
    }
}
