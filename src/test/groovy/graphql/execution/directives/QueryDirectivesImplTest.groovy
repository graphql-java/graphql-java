package graphql.execution.directives

import graphql.language.DirectivesContainer
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.IgnoredChars
import graphql.language.OperationDefinition
import graphql.schema.GraphQLDirective
import spock.lang.Specification

import static graphql.schema.GraphQLDirective.newDirective

class QueryDirectivesImplTest extends Specification {

    GraphQLDirective cachedDirective = newDirective().name("cached").build()
    GraphQLDirective timeOutDirective = newDirective().name("timeout").build()
    GraphQLDirective logDirective = newDirective().name("log").build()
    GraphQLDirective upperDirective = newDirective().name("upper").build()
    GraphQLDirective lowerDirective = newDirective().name("lower").build()

    AstNodeDirectivesImpl info(int distance, Map<String, GraphQLDirective> directives) {
        return new AstNodeDirectivesImpl(new Field("ignored"), distance, directives)
    }

    AstNodeDirectivesImpl info(DirectivesContainer container, int distance, Map<String, GraphQLDirective> directives) {
        return new AstNodeDirectivesImpl(container, distance, directives)
    }

    FragmentDefinition mkFragDef(String name) {
        FragmentDefinition.newFragmentDefinition().name(name)
                .comments([]).ignoredChars(IgnoredChars.EMPTY).build()
    }

    OperationDefinition mkOpDef(String name) {
        OperationDefinition.newOperationDefinition().name(name)
                .comments([]).ignoredChars(IgnoredChars.EMPTY).operation(OperationDefinition.Operation.QUERY).build()
    }


    def "can get immediate directives that is distance 0"() {

        def infos = [
                info(0, [cached: cachedDirective, upper: upperDirective]),
                info(1, [log: logDirective]),
                info(2, [lower: lowerDirective]),
                info(0, [cached: cachedDirective, timeOut: timeOutDirective]),

        ]
        def impl = new QueryDirectivesImpl(infos)

        when:
        def directives = impl.getImmediateDirectives()
        then:
        directives == [cached: [cachedDirective, cachedDirective], upper: [upperDirective], timeOut: [timeOutDirective]]

        when:
        def result = impl.getImmediateDirective("cached")
        then:
        result == [cachedDirective, cachedDirective]
    }

}
