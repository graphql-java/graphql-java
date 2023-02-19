package graphql.schema

import graphql.GraphQLException
import graphql.Scalars
import spock.lang.Specification

class PropertyDataFetcherClassLoadingTest extends Specification {

    GraphQLFieldDefinition fld(String fldName) {
        return GraphQLFieldDefinition.newFieldDefinition().name(fldName).type(Scalars.GraphQLString).build()
    }

    static class BrokenClass {
        static {
            // this should prevent it from existing
            throw new RuntimeException("No soup for you!")
        }
    }


    static class TargetClass {

        String getOkThings() {
            return "ok"
        }

        BrokenClass getBrokenThings() {
            return BrokenClass.cast(null)
        }
    }

    def "can survive linkage errors during access to broken classes in Lambda support"() {
        def okDF = PropertyDataFetcher.fetching("okThings")
        def brokenDF = PropertyDataFetcher.fetching("brokenThings")

        def target = new TargetClass()

        when:
        def value = okDF.get(fld("okThings"), target, { -> null })
        then:
        value == "ok"

        when:
        brokenDF.get(fld("brokenThings"), target, { -> null })
        then:
        // This is because the reflection method finder cant get to it
        // but it has made it past the Meta Lambda support
        thrown(GraphQLException)

        // multiple times  - same result
        when:
        value = okDF.get(fld("okThings"), target, { -> null })
        then:
        value == "ok"

        when:
        brokenDF.get(fld("brokenThings"), target, { -> null })
        then:
        thrown(GraphQLException)

    }
}
