package graphql.execution.directives

import graphql.language.DirectivesContainer
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.IgnoredChars
import graphql.language.OperationDefinition
import graphql.schema.GraphQLDirective
import spock.lang.Specification

import static graphql.Scalars.GraphQLString
import static graphql.schema.GraphQLDirective.*

class FieldDirectivesImplTest extends Specification {

    GraphQLDirective cachedDirective = newDirective().name("cached").build()
    GraphQLDirective cachedDirective2 = newDirective().name("cached").argument({
        it.name("arg1").type(GraphQLString)
    }).build()
    GraphQLDirective timeOutDirective = newDirective().name("timeout").build()
    GraphQLDirective logDirective = newDirective().name("log").build()
    GraphQLDirective upperDirective = newDirective().name("upper").build()
    GraphQLDirective lowerDirective = newDirective().name("lower").build()

    FieldDirectivesInfoImpl directivePos(int distance, Map<String, GraphQLDirective> directives) {
        return new FieldDirectivesInfoImpl(new Field("ignored"), distance, directives)
    }

    FieldDirectivesInfoImpl directivePos(DirectivesContainer container, int distance, Map<String, GraphQLDirective> directives) {
        return new FieldDirectivesInfoImpl(container, distance, directives)
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

        def directivePositions = [
                directivePos(0, [cached: cachedDirective, upper: upperDirective]),
                directivePos(1, [log: logDirective]),
                directivePos(2, [lower: lowerDirective]),
                directivePos(0, [cached: cachedDirective, timeOut: timeOutDirective]),

        ]
        def impl = new FieldDirectivesImpl(directivePositions)

        when:
        def directives = impl.getImmediateDirectives()
        then:
        directives == [cached: [cachedDirective, cachedDirective], upper: [upperDirective], timeOut: [timeOutDirective]]

        when:
        def result = impl.getImmediateDirective("cached")
        then:
        result == [cachedDirective, cachedDirective]
    }

    def "can get closest named directives"() {
        def directivePositions = [
                directivePos(0, [upper: upperDirective]),
                directivePos(1, [log: logDirective]),
                directivePos(2, [lower: lowerDirective, cached: cachedDirective]),
                directivePos(2, [upper: upperDirective, cached: cachedDirective2]),
                directivePos(3, [cached: cachedDirective, log: logDirective]),
                directivePos(0, [timeOut: timeOutDirective]),

        ]
        def impl = new FieldDirectivesImpl(directivePositions)

        when:
        def directives = impl.getClosestDirective("cached")
        then:
        directives == [cachedDirective, cachedDirective2]

        when:
        directives = impl.getClosestDirective("unknown")
        then:
        directives == []
    }

    def fragmentSpread = new FragmentSpread("spread")
    def fragmentDefinition = mkFragDef("fragDef")
    def operationDefinition = mkOpDef("queryName")
    def fieldF = new Field("f")
    def fieldG = new Field("g")
    def fieldH = new Field("h")

    def unsortedDirectivePositions = [
            directivePos(fieldF, 0, [upper: upperDirective]),
            directivePos(fragmentSpread, 1, [log: logDirective]),
            directivePos(fragmentDefinition, 1, [lower: lowerDirective, cached: cachedDirective]),
            directivePos(operationDefinition, 3, [cached: cachedDirective, log: logDirective]),
            directivePos(fieldG, 2, [upper: upperDirective, cached: cachedDirective2]),
            directivePos(fieldH, 0, [timeOut: timeOutDirective]),

    ]


    def "get all directive positions"() {
        def impl = new FieldDirectivesImpl(unsortedDirectivePositions)

        when:
        def directives = impl.getAllDirectives()
        then:
        directives == [
                directivePos(fieldF, 0, [upper: upperDirective]),
                directivePos(fieldH, 0, [timeOut: timeOutDirective]),

                // sorts by name if distance equal
                directivePos(fragmentDefinition, 1, [lower: lowerDirective, cached: cachedDirective]),
                directivePos(fragmentSpread, 1, [log: logDirective]),

                directivePos(fieldG, 2, [upper: upperDirective, cached: cachedDirective2]),

                directivePos(operationDefinition, 3, [cached: cachedDirective, log: logDirective]),
        ]
    }

    def "get all directive positions with a certain name"() {
        def impl = new FieldDirectivesImpl(unsortedDirectivePositions)

        when:
        def directives = impl.getAllDirectivesNamed("cached")
        then:
        directives == [
                directivePos(fragmentDefinition, 1, [cached: cachedDirective]),

                directivePos(fieldG, 2, [cached: cachedDirective2]),

                directivePos(operationDefinition, 3, [cached: cachedDirective]),
        ]
    }
}
