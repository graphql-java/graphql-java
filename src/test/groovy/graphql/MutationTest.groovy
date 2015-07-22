package graphql

import spock.lang.Specification


class MutationTest extends Specification {


    def "evaluates mutations"() {
        given:
        def query = """
            mutation M {
              first: changeTheNumber(newNumber: 1) {
                theNumber
              },
              second: changeTheNumber(newNumber: 2) {
                theNumber
              },
              third: changeTheNumber(newNumber: 3) {
                theNumber
              }
              fourth: changeTheNumber(newNumber: 4) {
                theNumber
              },
              fifth: changeTheNumber(newNumber: 5) {
                theNumber
              }
            }
            """

        def expectedResult = [
                first : [
                        theNumber: 1
                ],
                second: [
                        theNumber: 2
                ],
                third : [
                        theNumber: 3
                ],
                fourth: [
                        theNumber: 4
                ],
                fifth : [
                        theNumber: 5
                ]
        ]

        when:
        def executionResult = new GraphQL(MutationSchema.schema).execute(query, new MutationSchema.Root(6))


        then:
        executionResult.result == expectedResult

    }


    def "evaluates mutations with errors"() {
        given:
        def query = """
            mutation M {
              first: changeTheNumber(newNumber: 1) {
                theNumber
              },
              second: changeTheNumber(newNumber: 2) {
                theNumber
              },
              third: failToChangeTheNumber(newNumber: 3) {
                theNumber
              }
              fourth: changeTheNumber(newNumber: 4) {
                theNumber
              },
              fifth: failToChangeTheNumber(newNumber: 5) {
                theNumber
              }
            }
            """

        def expectedResult = [
                first : [
                        theNumber: 1
                ],
                second: [
                        theNumber: 2
                ],
                third : null,
                fourth: [
                        theNumber: 4
                ],
                fifth : null
        ]

        when:
        def executionResult = new GraphQL(MutationSchema.schema).execute(query, new MutationSchema.Root(6))


        then:
        executionResult.result == expectedResult
        executionResult.errors.size() == 2
        executionResult.errors.every({ it instanceof ExceptionWhileDataFetching })

    }
}
