package graphql.schema

import graphql.introspection.Introspection
import spock.lang.Specification

import java.util.Collections;

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLString
import static graphql.StarWarsSchema.*

class SchemaUtilTest extends Specification {

    def "collectAllTypes"() {
        when:
        Map<String, GraphQLType> types = new SchemaUtil().allTypes(starWarsSchema, Collections.emptySet())
        then:
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
                  (GraphQLBoolean.name)            : GraphQLBoolean]
    }
}
