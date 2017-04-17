package graphql.schema

import graphql.NestedInputSchema
import graphql.TypeReferenceSchema
import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.Scalars.*
import static graphql.StarWarsSchema.*
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectType.newInputObject
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLInputObjectField.newInputObjectField
import static TypeReferenceSchema.SchemaWithReferences

class SchemaUtilTest extends Specification {

    def "collectAllTypes"() {
        when:
        Map<String, GraphQLType> types = new SchemaUtil().allTypes(starWarsSchema, Collections.emptySet())
        then:
        types.size() == 15
        types == [(droidType.name)                 : droidType,
                  (humanType.name)                 : humanType,
                  (queryType.name)                 : queryType,
                  (characterInterface.name)        : characterInterface,
                  (episodeEnum.name)               : episodeEnum,
                  (GraphQLString.name)             : GraphQLString,
                  (Introspection.__Schema.name)    : Introspection.__Schema,
                  (Introspection.__Type.name)      : Introspection.__Type,
                  (Introspection.__TypeKind.name)  : Introspection.__TypeKind,
                  (Introspection.__Field.name)     : Introspection.__Field,
                  (Introspection.__InputValue.name): Introspection.__InputValue,
                  (Introspection.__EnumValue.name) : Introspection.__EnumValue,
                  (Introspection.__Directive.name) : Introspection.__Directive,
                  (Introspection.__DirectiveLocation.name) : Introspection.__DirectiveLocation,
                  (GraphQLBoolean.name)            : GraphQLBoolean]
    }

    def "collectAllTypesNestedInput"() {
        when:
        Map<String, GraphQLType> types = new SchemaUtil().allTypes(NestedInputSchema.createSchema(), Collections.emptySet());
        Map<String, GraphQLType> expected =

         [(NestedInputSchema.rootType().name)                 : NestedInputSchema.rootType(),
                  (NestedInputSchema.filterType().name)                 : NestedInputSchema.filterType(),
                  (NestedInputSchema.rangeType().name)                 : NestedInputSchema.rangeType(),
                  (GraphQLInt.name)             : GraphQLInt,
                  (GraphQLString.name)             : GraphQLString,
                  (Introspection.__Schema.name)    : Introspection.__Schema,
                  (Introspection.__Type.name)      : Introspection.__Type,
                  (Introspection.__TypeKind.name)  : Introspection.__TypeKind,
                  (Introspection.__Field.name)     : Introspection.__Field,
                  (Introspection.__InputValue.name): Introspection.__InputValue,
                  (Introspection.__EnumValue.name) : Introspection.__EnumValue,
          (Introspection.__Directive.name) : Introspection.__Directive,
          (Introspection.__DirectiveLocation.name) : Introspection.__DirectiveLocation,
          (GraphQLBoolean.name)            : GraphQLBoolean];
        then:
        types.keySet() == expected.keySet()
    }

    def "using reference to input as output results in error"() {
        given:
        GraphQLInputObjectType PersonInputType = newInputObject()
                .name("Person")
                .field(newInputObjectField()
                .name("name")
                .type(GraphQLString))
                .build();

        GraphQLFieldDefinition field = newFieldDefinition()
                .name("find")
                .type(new GraphQLTypeReference("Person"))
                .argument(newArgument()
                .name("ssn")
                .type(GraphQLString))
                .build();

        GraphQLObjectType PersonService = newObject()
                .name("PersonService")
                .field(field)
                .build();
        def schema = new GraphQLSchema(PersonService, null, Collections.singleton(PersonInputType))
        when:
        new SchemaUtil().replaceTypeReferences(schema)
        then:
        thrown(ClassCastException)
    }
    
    def "all references are replaced"() {
        given:
        GraphQLUnionType pet =  ((GraphQLUnionType)SchemaWithReferences.getType("Pet"));
        GraphQLObjectType person =  ((GraphQLObjectType)SchemaWithReferences.getType("Person"));
        when:
        new SchemaUtil().replaceTypeReferences(SchemaWithReferences)
        then:
        SchemaWithReferences.allTypesAsList.findIndexOf{it instanceof TypeReference} == -1;
        pet.types.findIndexOf{it instanceof TypeReference} == -1;
        person.interfaces.findIndexOf{it instanceof TypeReference} == -1;
    }
}
