package graphql.schema

import graphql.util.TraversalControl
import graphql.util.TraverserContext
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef

class GraphQLInterfaceTypeTest extends Specification {

    def "duplicate field definition overwrites existing value"() {
        when:
        def interfaceType = newInterface().name("TestInterfaceType")
                .description("description")
                .fields([
                        newFieldDefinition().name("NAME").type(GraphQLString).build(),
                        newFieldDefinition().name("NAME").type(GraphQLInt).build()
                ])
                .typeResolver(new TypeResolverProxy())
                .build()
        then:
        interfaceType.getName() == "TestInterfaceType"
        interfaceType.getFieldDefinition("NAME").getType() == GraphQLInt
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

    def "schema transformer accepts interface with type reference"() {
        given:
        def iFace = newInterface().name("iFace")
                    .field(builder -> builder.type(GraphQLString).name("field"))
                    .withInterface(typeRef("iFace2"))
                    .build()

        def iFace2 = newInterface().name("iFace2")
                    .field(builder -> builder.type(GraphQLString).name("field"))
                    .build()

        def impl = newObject().name("impl")
                    .field(builder -> builder.type(GraphQLString).name("field"))
                    .withInterfaces(typeRef("iFace"))
                    .withInterfaces(typeRef("iFace2"))
                    .build()

        def codeRegBuilder = newCodeRegistry().typeResolver(iFace, env -> impl)
                                .typeResolver(iFace2, env -> impl)

        def schema = newSchema()
                        .codeRegistry(codeRegBuilder.build())
                        .additionalType(iFace).additionalType(iFace2)
                        .query(newObject()
                                .name("test")
                                .field(builder -> builder.name("iFaceField").type(impl))
                        ).build()

        when:
        SchemaTransformer.transformSchema(schema, new GraphQLTypeVisitorStub() {
            @Override
            TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
                GraphQLFieldDefinition transform = node.transform(
                        builder -> builder.argument(builder1 -> builder1.name("arg").type(GraphQLString)))
                changeNode(context, transform)
                return super.visitGraphQLFieldDefinition(node, context)
            }
        })

        then:
        noExceptionThrown()
    }
}
