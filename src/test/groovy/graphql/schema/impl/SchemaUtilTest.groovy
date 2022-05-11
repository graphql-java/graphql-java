package graphql.schema.impl

import graphql.AssertException
import graphql.DirectivesUtil
import graphql.NestedInputSchema
import graphql.introspection.Introspection
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeReference
import graphql.schema.GraphQLUnionType
import graphql.schema.impl.SchemaUtil
import spock.lang.Specification

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.characterInterface
import static graphql.StarWarsSchema.droidType
import static graphql.StarWarsSchema.episodeEnum
import static graphql.StarWarsSchema.humanType
import static graphql.StarWarsSchema.inputHumanType
import static graphql.StarWarsSchema.mutationType
import static graphql.StarWarsSchema.queryType
import static graphql.StarWarsSchema.starWarsSchema
import static graphql.TypeReferenceSchema.ArgumentDirectiveInput
import static graphql.TypeReferenceSchema.Cache
import static graphql.TypeReferenceSchema.EnumDirectiveInput
import static graphql.TypeReferenceSchema.EnumValueDirectiveInput
import static graphql.TypeReferenceSchema.FieldDefDirectiveInput
import static graphql.TypeReferenceSchema.InputFieldDefDirectiveInput
import static graphql.TypeReferenceSchema.InputObjectDirectiveInput
import static graphql.TypeReferenceSchema.InterfaceDirectiveInput
import static graphql.TypeReferenceSchema.ObjectDirectiveInput
import static graphql.TypeReferenceSchema.QueryDirectiveInput
import static graphql.TypeReferenceSchema.SchemaWithReferences
import static graphql.TypeReferenceSchema.UnionDirectiveInput
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLSchema.newSchema
import static graphql.schema.GraphQLTypeReference.typeRef

class SchemaUtilTest extends Specification {

    def "collectAllTypes"() {
        when:
        def collectingVisitor = new GraphQLTypeCollectingVisitor()
        SchemaUtil.visitPartiallySchema(starWarsSchema, collectingVisitor)
        Map<String, GraphQLType> types = collectingVisitor.getResult()
        then:
        types.size() == 17
        types == [(droidType.name)                        : droidType,
                  (humanType.name)                        : humanType,
                  (queryType.name)                        : queryType,
                  (mutationType.name)                     : mutationType,
                  (characterInterface.name)               : characterInterface,
                  (inputHumanType.name)                   : inputHumanType,
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
        def collectingVisitor = new GraphQLTypeCollectingVisitor()
        SchemaUtil.visitPartiallySchema(NestedInputSchema.createSchema(), collectingVisitor)
        Map<String, GraphQLType> types = collectingVisitor.getResult()
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

    def "collect all types defined in directives"() {
        when:
        def collectingVisitor = new GraphQLTypeCollectingVisitor()
        SchemaUtil.visitPartiallySchema(SchemaWithReferences, collectingVisitor)
        Map<String, GraphQLType> types = collectingVisitor.getResult()

        then:
        types.size() == 30
        types.containsValue(UnionDirectiveInput)
        types.containsValue(InputObjectDirectiveInput)
        types.containsValue(ObjectDirectiveInput)
        types.containsValue(FieldDefDirectiveInput)
        types.containsValue(ArgumentDirectiveInput)
        types.containsValue(InputFieldDefDirectiveInput)
        types.containsValue(InterfaceDirectiveInput)
        types.containsValue(EnumDirectiveInput)
        types.containsValue(EnumValueDirectiveInput)
        types.containsValue(QueryDirectiveInput)
    }

    def "group all types by implemented interface"() {
        when:
        Map<String, List<GraphQLObjectType>> byInterface = SchemaUtil.groupInterfaceImplementationsByName(starWarsSchema.getAllTypesAsList())

        then:
        byInterface.size() == 1
        byInterface[characterInterface.getName()].size() == 2
        byInterface == [
                (characterInterface.getName()): [droidType, humanType]
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

        when:
        newSchema().query(PersonService).additionalType(PersonInputType).build()
        then:
        thrown(ClassCastException)
    }

    def "all references are replaced"() {
        when:
        GraphQLUnionType pet = ((GraphQLUnionType) SchemaWithReferences.getType("Pet"))
        GraphQLObjectType person = ((GraphQLObjectType) SchemaWithReferences.getType("Person"))
        GraphQLArgument cacheEnabled = DirectivesUtil.directiveWithArg(
                SchemaWithReferences.getDirectives(), Cache.getName(), "enabled").get();
        then:
        SchemaWithReferences.allTypesAsList.findIndexOf { it instanceof GraphQLTypeReference } == -1
        pet.types.findIndexOf { it instanceof GraphQLTypeReference } == -1
        person.interfaces.findIndexOf { it instanceof GraphQLTypeReference } == -1
        !(cacheEnabled.getType() instanceof GraphQLTypeReference)
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

        newSchema().query(queryType).mutation(mutation).build()

        then:

        def e = thrown(AssertException)
        e.getMessage().contains("All types within a GraphQL schema must have unique names")

    }
}
