package graphql.execution

import graphql.GraphQL
import graphql.StarWarsSchema
import spock.lang.Specification

class ExecutionConstraintsTest extends Specification {

    def "default built object imposes no constraints"() {
        given:
        when:
        def constraints = ExecutionConstraints.newConstraints()
                .build()

        then:
        constraints.getMaxQueryDepth() < 0
        constraints.getQueryDepth() == 0
    }

    def "builder will record intended constraint values"() {
        given:
        when:
        def constraints = ExecutionConstraints
                .newConstraints()
                .maxQueryDepth(99)
                .build()

        then:
        constraints.getMaxQueryDepth() == 99
        constraints.getQueryDepth() == 0
    }

    def "descending into a call will increment and decrement the depth "() {
        given:
        when:
        def constraints = ExecutionConstraints
                .newConstraints()
                .maxQueryDepth(2)
                .build()

        def expectedDepth1In = 0
        def expectedDepth1Out = 0
        def expectedDepth2In = 0

        constraints.callTrackingDepth({
            expectedDepth1In = constraints.getQueryDepth()
            constraints.callTrackingDepth({
                expectedDepth2In = constraints.getQueryDepth()
            })
            expectedDepth1Out = constraints.getQueryDepth()
        })

        then:
        expectedDepth1In == 1
        expectedDepth2In == 2
        expectedDepth1Out == 1
    }

    def "exceeding maximum depth will be detected"() {

        given:
        when:
        def constraints = ExecutionConstraints
                .newConstraints()
                .maxQueryDepth(2)
                .build()

        def expected1 = false
        def expected2 = false
        def expected3 = false

        constraints.callTrackingDepth({
            expected1 = constraints.hasExceededMaxDepthConstraint()
            constraints.callTrackingDepth({
                expected2 = constraints.hasExceededMaxDepthConstraint()
                constraints.callTrackingDepth({
                    expected3 = constraints.hasExceededMaxDepthConstraint()
                })
            })
        })

        then:
        !expected1
        !expected2
        expected3
    }

    def "unlimited maximum depth can never be exceeded"() {

        given:
        when:
        def constraints = ExecutionConstraints
                .newConstraints()
                .build()

        def expected1 = false
        def expected2 = false
        def expected3 = false

        constraints.callTrackingDepth({
            expected1 = constraints.hasExceededMaxDepthConstraint()
            constraints.callTrackingDepth({
                expected2 = constraints.hasExceededMaxDepthConstraint()
                constraints.callTrackingDepth({
                    expected3 = constraints.hasExceededMaxDepthConstraint()
                })
            })
        })

        then:
        !expected1
        !expected2
        !expected3
    }

    def query = """
        query NestedQuery {
            hero {
                name
                friends {
                    name
                    appearsIn
                    friends {
                        name
                    }
                }
            }
        }
        """
    def expectedWithoutConstraints = [
            hero: [name   : 'R2-D2',
                   friends: [
                           [
                                   name     :
                                           'Luke Skywalker',
                                   appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                   friends  : [
                                           [
                                                   name: 'Han Solo',
                                           ],
                                           [
                                                   name: 'Leia Organa',
                                           ],
                                           [
                                                   name: 'C-3PO',
                                           ],
                                           [
                                                   name: 'R2-D2',
                                           ],
                                   ]
                           ],
                           [
                                   name     : 'Han Solo',
                                   appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                   friends  : [
                                           [
                                                   name: 'Luke Skywalker',
                                           ],
                                           [
                                                   name: 'Leia Organa',
                                           ],
                                           [
                                                   name: 'R2-D2',
                                           ],
                                   ]
                           ],
                           [
                                   name     : 'Leia Organa',
                                   appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                   friends  : [
                                           [
                                                   name: 'Luke Skywalker',
                                           ],
                                           [
                                                   name: 'Han Solo',
                                           ],
                                           [
                                                   name: 'C-3PO',
                                           ],
                                           [
                                                   name: 'R2-D2',
                                           ],
                                   ]
                           ],
                   ]
            ]
    ]

    def expectedWithDepth3 = [
            hero: [name   : 'R2-D2',
                   friends: [
                           [
                                   name     :
                                           'Luke Skywalker',
                                   appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                   friends  : [
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                   ]
                           ],
                           [
                                   name     : 'Han Solo',
                                   appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                   friends  : [
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                   ]
                           ],
                           [
                                   name     : 'Leia Organa',
                                   appearsIn: ['NEWHOPE', 'EMPIRE', 'JEDI'],
                                   friends  : [
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                           [
                                                   name: null,
                                           ],
                                   ]
                           ],
                   ]
            ]
    ]


    def 'Shows full data when the max depth is not exceeded'() {
        given:

        when:
        def result = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .maxQueryDepth(99)
                .build().execute(query).data

        then:
        result == expectedWithoutConstraints
    }

    def 'Shows null when the depth is exceeded'() {
        given:

        when:
        def result = GraphQL
                .newGraphQL(StarWarsSchema.starWarsSchema)
                .maxQueryDepth(3)
                .build().execute(query).data

        then:
        result == expectedWithDepth3
    }

}
