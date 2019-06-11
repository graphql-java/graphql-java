package graphql.schema

import spock.lang.Specification

import static graphql.schema.GraphQLDirective.newDirective

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
                .withDirective(newDirective().name("directive1"))
                .withDirective(newDirective().name("directive2"))
                .build()
        when:
        def transformedScalar = startingScalar.transform({ builder ->
            builder.name("S2")
                    .description("S2_description")
                    .withDirective(newDirective().name("directive1"))
                    .withDirective(newDirective().name("directive3"))
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
