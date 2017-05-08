package graphql.schema.validation

import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.TypeResolver
import spock.lang.Specification

import static SchemaValidationErrorType.ObjectDoesNotImplementItsInterfaces
import static graphql.Scalars.*
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLNonNull.nonNull

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

            .field(newFieldDefinition().name("argField1").type(GraphQLString)
            .argument(newArgument().name("arg1").type(GraphQLString))
            .argument(newArgument().name("arg2").type(GraphQLInt))
            .argument(newArgument().name("arg3").type(GraphQLBoolean))
            .argument(newArgument().name("arg4").type(GraphQLString).defaultValue("ABC"))
    )

            .field(newFieldDefinition().name("argField2").type(GraphQLString)
            .argument(newArgument().name("arg1").type(GraphQLString))
            .argument(newArgument().name("arg2").type(GraphQLInt))
            .argument(newArgument().name("arg3").type(GraphQLBoolean))
    )
            .typeResolver(typeResolver)
            .build()

    def "objects implement interfaces"() {
        given:

        SchemaValidationErrorCollector errorCollector = new SchemaValidationErrorCollector()
        GraphQLObjectType objType = GraphQLObjectType.newObject()
                .name("obj")
                .withInterface(InterfaceType)
                .field(newFieldDefinition().name("name").type(GraphQLString))
                .field(newFieldDefinition().name("missing").type(list(GraphQLString)))
                .field(newFieldDefinition().name("age").type(GraphQLString))
                .field(newFieldDefinition().name("address").type(list(nonNull(GraphQLString))))

                .field(newFieldDefinition().name("argField1").type(GraphQLString)
                .argument(newArgument().name("arg1").type(GraphQLInt))
                .argument(newArgument().name("arg2").type(GraphQLInt))
                .argument(newArgument().name("arg3").type(GraphQLInt))
                .argument(newArgument().name("arg4").type(GraphQLString).defaultValue("XYZ"))
        )

                .field(newFieldDefinition().name("argField2").type(GraphQLString)
                .argument(newArgument().name("arg1").type(GraphQLString))
        )

                .build()

        when:
        new ObjectsImplementInterfaces().check(objType, errorCollector)

        then:

        errorCollector.containsValidationError(ObjectDoesNotImplementItsInterfaces)
        def errors = errorCollector.getErrors()
        errors.size() == 7
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'friends' is missing"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'age' is defined as 'String' type and not as 'Int' type"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'address' is defined as '[String!]' type and not as '[String]' type"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'address' is defined as '[String!]' type and not as '[String]' type"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg1' is defined differently"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField1' argument 'arg1' is defined differently"))
        errors.contains(new SchemaValidationError(ObjectDoesNotImplementItsInterfaces,
                "object type 'obj' does not implement interface 'Interface' because field 'argField2' has a different number of arguments"))
    }
}
