package graphql.schema

import graphql.AssertException
import spock.lang.Specification

import static graphql.language.Directive.newDirective

class GraphQLAppliedDirectiveTest extends Specification {

    def "getNonNullArgument returns argument when it exists"() {
        given:
        def directive = GraphQLAppliedDirective.newDirective()
                .name("myDirective")
                .definition(newDirective().name("myDirective").build())
                .argument(GraphQLAppliedDirectiveArgument.newArgument()
                        .name("argName")
                        .type(graphql.Scalars.GraphQLString)
                        .build())
                .build()

        when:
        def argument = directive.getNonNullArgument("argName")

        then:
        argument != null
        argument.getName() == "argName"
    }

    def "getNonNullArgument throws when argument does not exist"() {
        given:
        def directive = GraphQLAppliedDirective.newDirective()
                .name("myDirective")
                .definition(newDirective().name("myDirective").build())
                .build()

        when:
        directive.getNonNullArgument("nonExistent")

        then:
        thrown(AssertException)
    }
}
