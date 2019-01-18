package graphql.execution.directives


import graphql.execution.directives.FieldDirectiveCollector
import graphql.language.DirectivesContainer
import graphql.language.Field
import graphql.schema.GraphQLDirective
import spock.lang.Specification

import static graphql.execution.MergedField.newMergedField
import static graphql.language.Field.newField
import static graphql.schema.GraphQLDirective.newDirective

class FieldDirectiveCollectorTest extends Specification {

    GraphQLDirective cachedDirective = newDirective().name("cached").build()
    GraphQLDirective timeOutDirective = newDirective().name("timeout").build()
    GraphQLDirective logDirective = newDirective().name("log").build()
    GraphQLDirective upperDirective = newDirective().name("upper").build()
    GraphQLDirective lowerDirective = newDirective().name("lower").build()

    FieldDirectivesInfo directivePos(int distance, Map<String, GraphQLDirective> directives) {
        return new FieldDirectivesInfoImpl(new Field("ignored"), distance, directives)
    }

    FieldDirectivesInfo directivePos(DirectivesContainer container, int distance, Map<String, GraphQLDirective> directives) {
        return new FieldDirectivesInfoImpl(container, distance, directives)
    }

    def "combine works as expected"() {
        def field1 = newField("f1").build()
        def field2 = newField("f2").build()
        def field3 = newField("f2").build()
        def mergedField = newMergedField([field1, field2]).build()
        def allInfo = [
                (field1): [
                        directivePos(field1, 2, [lower: lowerDirective]),
                        directivePos(field1, 1, [cached: cachedDirective, log: logDirective]),
                ],

                (field2): [
                        directivePos(field2, 0, [timeOut: timeOutDirective]),
                        directivePos(field2, 4, [lower: lowerDirective, upper: upperDirective]),
                ],

                (field3): [
                        directivePos(field3, 3, [upper: upperDirective, log: logDirective]),
                        directivePos(field3, 6, [timeOut: timeOutDirective]),
                ],
        ] as Map


        when:
        def directivesForField = new FieldDirectiveCollector().combineDirectivesForField(mergedField, allInfo)
        then:
        directivesForField.size() == 4
        // sorted by distance
        directivesForField == [
                directivePos(field2, 0, [lower: lowerDirective, upper: upperDirective]),
                directivePos(field1, 1, [cached: cachedDirective, log: logDirective]),
                directivePos(field1, 2, [lower: lowerDirective]),
                directivePos(field2, 4, [timeOut: timeOutDirective]),
        ]

    }
}
