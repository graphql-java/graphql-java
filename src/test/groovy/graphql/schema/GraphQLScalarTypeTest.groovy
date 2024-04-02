package graphql.schema

import graphql.introspection.Introspection
import spock.lang.Specification

import static graphql.TestUtil.mkDirective

class GraphQLScalarTypeTest extends Specification {
    Coercing<String, String> coercing = new Coercing<String, String>() {
        @Override
        String serialize(Object dataFetcherResult) throws CoercingSerializeException {
            return null
        }

        @Override
        String parseValue(Object input) throws CoercingParseValueException {
            return null
        }

        @Override
        String parseLiteral(Object input) throws CoercingParseLiteralException {
            return null
        }
    }

    def "builder works as expected"() {
        given:
        def startingScalar = GraphQLScalarType.newScalar()
                .name("S1")
                .description("S1_description")
                .coercing(coercing)
                .withDirective(mkDirective("directive1", Introspection.DirectiveLocation.SCALAR))
                .withDirective(mkDirective("directive2", Introspection.DirectiveLocation.SCALAR))
                .build()
        when:
        def transformedScalar = startingScalar.transform({ builder ->
            builder.name("S2")
                    .description("S2_description")
                    .withDirective(mkDirective("directive3", Introspection.DirectiveLocation.SCALAR))
        })

        then:
        startingScalar.getName() == "S1"
        startingScalar.getDescription() == "S1_description"
        startingScalar.getCoercing() == coercing

        startingScalar.getDirectives().size() == 2
        startingScalar.getDirective("directive1") != null
        startingScalar.getDirective("directive2") != null

        transformedScalar.name == "S2"
        transformedScalar.description == "S2_description"
        startingScalar.getCoercing() == coercing

        transformedScalar.getDirectives().size() == 3
        transformedScalar.getDirective("directive1") != null
        transformedScalar.getDirective("directive2") != null
        transformedScalar.getDirective("directive3") != null
    }
}
