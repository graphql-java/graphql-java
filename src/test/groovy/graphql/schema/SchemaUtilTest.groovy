package graphql.schema

import graphql.AssertException
import graphql.NestedInputSchema
import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.characterInterface
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.episodeEnum
import static graphql.StarWarsSchema.humanType
import static graphql.StarWarsSchema.queryType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.TypeReferenceSchema.SchemaWithReferences
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLTypeReference.typeRef

class SchemaUtilTest extends Specification {

    def "collectAllTypes"() {
        when:
        Map<String, GraphQLType> types = new SchemaUtil().allTypes(starWarsSchema, Collections.emptySet())
        then:
        types.size() == 15
        types == [(droidType.name)                        : droidType,
                  (humanType.name)                        : humanType,
                  (queryType.name)                        : queryType,
                  (characterInterface.name)               : characterInterface,
                  (episodeEnum.name)                      : episodeEnum,
                  (GraphQLString.name)                    : GraphQLString,
                  (Introspection.__Schema.name)           : Introspection.__Schema,
                  (Introspection.__Type.name)             : Introspection.__Type,
                  (Introspection.__TypeKind.name)         : Introspection.__TypeKind,
                  (Introspection.__Field.name)            : Introspection.__Field,
                  (Introspection.__InputValue.name)       : Introspection.__InputValue,
                  (Introspection.__EnumValue.name)        : Introspection.__EnumValue,
                  (Introspection.__Directive.name)        : Introspection.__Directive,
                  (Introspection.__DirectiveLocation.name): Introspection.__DirectiveLocation,
                  (GraphQLBoolean.name)                   : GraphQLBoolean]
    }

    def "collectAllTypesNestedInput"() {
        when:
        Map<String, GraphQLType> types = new SchemaUtil().allTypes(NestedInputSchema.createSchema(), Collections.emptySet())
        Map<String, GraphQLType> expected =

                [(NestedInputSchema.rootType().name)     : NestedInputSchema.rootType(),
                 (NestedInputSchema.filterType().name)   : NestedInputSchema.filterType(),
                 (NestedInputSchema.rangeType().name)    : NestedInputSchema.rangeType(),
                 (GraphQLInt.name)                       : GraphQLInt,
                 (GraphQLString.name)                    : GraphQLString,
                 (Introspection.__Schema.name)           : Introspection.__Schema,
                 (Introspection.__Type.name)             : Introspection.__Type,
                 (Introspection.__TypeKind.name)         : Introspection.__TypeKind,
                 (Introspection.__Field.name)            : Introspection.__Field,
                 (Introspection.__InputValue.name)       : Introspection.__InputValue,
                 (Introspection.__EnumValue.name)        : Introspection.__EnumValue,
                 (Introspection.__Directive.name)        : Introspection.__Directive,
                 (Introspection.__DirectiveLocation.name): Introspection.__DirectiveLocation,
                 (GraphQLBoolean.name)                   : GraphQLBoolean]
        then:
        types.keySet() == expected.keySet()
    }

    def "group all types by implemented interface"() {
        when:
        Map<String, List<GraphQLObjectType>> byInterface = new SchemaUtil().groupImplementations(starWarsSchema)

        then:
        byInterface.size() == 1
        byInterface[characterInterface.getName()].size() == 2
        byInterface == [
                (characterInterface.getName()): [humanType, droidType]
        ]
    }

    def "using reference to input as output results in error"() {
        given:
        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(newInputObjectField()
                .name("name")
                .type(GraphQLString))
                .build()

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("find")
                .type(typeRef("Person"))
                .argument(newArgument()
                .name("ssn")
                .type(GraphQLString))
                .build()

        GraphQLObjectType PersonService = newObject()
                .name("PersonService")
                .field(field)
                .build()
        def schema = new GraphQLSchema(PersonService, null, Collections.singleton(PersonInputType))
        when:
        new SchemaUtil().replaceTypeReferences(schema)
        then:
        thrown(ClassCastException)
    }

    def "all references are replaced"() {
        given:
        GraphQLUnionType pet = ((GraphQLUnionType) SchemaWithReferences.getType("Pet"))
        GraphQLObjectType person = ((GraphQLObjectType) SchemaWithReferences.getType("Person"))
        when:
        new SchemaUtil().replaceTypeReferences(SchemaWithReferences)
        then:
        SchemaWithReferences.allTypesAsList.findIndexOf { it instanceof GraphQLTypeReference } == -1
        pet.types.findIndexOf { it instanceof GraphQLTypeReference } == -1
        person.interfaces.findIndexOf { it instanceof GraphQLTypeReference } == -1
    }

    def "redefined types are caught"() {
        when:
        final GraphQLInputObjectType attributeListInputObjectType = newInputObject().name("attributes")
                .description("attribute")
                .field(newInputObjectField().type(GraphQLString).name("key").build())
                .field(newInputObjectField().type(GraphQLString).name("value").build())
                .build()

        final GraphQLObjectType attributeListObjectType = newObject().name("attributes").description("attribute")
                .field(newFieldDefinition().type(GraphQLString).name("key").build())
                .field(newFieldDefinition().type(GraphQLString).name("value").build())
                .build()

        final GraphQLObjectType systemForMutation = newObject().name("systems").description("systems")
                .field(newFieldDefinition().name("attributes").type(list(attributeListInputObjectType)).build())
                .field(newFieldDefinition().name("attributes1").type(attributeListObjectType).build())
                .field(newFieldDefinition().type(typeRef("systems")).name("parentSystem").build())
                .build()

        final GraphQLObjectType systemForQuery = newObject().name("systems").description("systems")
                .field(newFieldDefinition().name("attributes").type(list(attributeListObjectType)).build())
                .field(newFieldDefinition().name("attributes1").type(attributeListObjectType).build())
                .field(newFieldDefinition().type(typeRef("systems")).name("parentSystem").build())
                .build()

        final GraphQLFieldDefinition systemWithArgsForMutation = newFieldDefinition().name("systems").type(systemForMutation)
                .argument(newArgument().name("attributes").type(list(attributeListInputObjectType)).build())
                .argument(newArgument().name("attributes1").type(attributeListInputObjectType).build())
                .build()

        final GraphQLFieldDefinition systemWithArgsForQuery = newFieldDefinition().name("systems").type(systemForQuery)
                .argument(newArgument().name("attributes").type(list(attributeListObjectType)).build())
                .argument(newArgument().name("attributes1").type(attributeListInputObjectType).build())
                .build()
        final GraphQLObjectType queryType = newObject().name("query").field(systemWithArgsForQuery)
                .build()
        final GraphQLObjectType mutation = newObject().name("mutation").field(systemWithArgsForMutation)
                .build()

        GraphQLSchema.newSchema().query(queryType).mutation(mutation).build()

        then:

        def e = thrown(AssertException)
        e.getMessage().contains("All types within a GraphQL schema must have unique names")

    }
}
