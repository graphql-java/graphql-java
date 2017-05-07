package graphql.schema.validation

import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import spock.lang.Specification

import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull
import static graphql.schema.validation.ValidationErrorType.ObjectDoesNotImplementItsInterfaces

class ObjectsImplementInterfacesTest extends Specification {

    TypeResolver typeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            null
        }
    }

    GraphQLInterfaceType InterfaceType = newInterface()
            .name("Interface")

            .field(newFieldDefinition().name("name").type(GraphQLString))
            .field(newFieldDefinition().name("friends").type(list(GraphQLString)))
            .field(newFieldDefinition().name("age").type(GraphQLInt))
            .field(newFieldDefinition().name("address").type(list(GraphQLString)))
            .typeResolver(typeResolver)
            .build()

    def "objects implement interfaces"() {
        given:

        ValidationErrorCollector errorCollector = new ValidationErrorCollector()
        GraphQLObjectType objType = GraphQLObjectType.newObject()
                .name("obj")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .field(newFieldDefinition().name("missing").type(list(GraphQLString)))
                .field(newFieldDefinition().name("age").type(GraphQLString))
                .field(newFieldDefinition().name("address").type(list(nonNull(GraphQLString))))
                .build()

        when:
        new ObjectsImplementInterfaces().check(objType, errorCollector)

        then:

        errorCollector.containsValidationError(ObjectDoesNotImplementItsInterfaces)
        def errors = errorCollector.getErrors()
        errors.size() == 3
        errors.contains(new ValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'friends' is missing"))
        errors.contains(new ValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'age' is defined as 'String' type and not as 'Int' type"))
        errors.contains(new ValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'address' is defined as '[String!]' type and not as '[String]' type"))
    }
}
