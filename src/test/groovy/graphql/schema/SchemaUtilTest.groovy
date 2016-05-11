package graphql.schema

import graphql.NestedInputSchema
import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.Scalars.*
import static graphql.StarWarsSchema.*

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

}
