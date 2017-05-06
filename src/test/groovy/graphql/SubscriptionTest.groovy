package graphql

import spock.lang.Specification

class SubscriptionTest extends Specification {

    def "evaluates subscriptions"() {
        given:
        def subscription1 = """
            subscription S {
              first: changeNumberSubscribe(clientId: 101) {
                theNumber
              }
            }
            """
        def subscription2 = """
            subscription S {
              first: changeNumberSubscribe(clientId: 102) {
                theNumber
              }
            }
            """

        def mutation = """
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
                "Alert client [101] that number is now [1]",
                "Alert client [101] that number is now [2]",
                "Alert client [101] that number is now [4]",
                "Alert client [101] that number is now [1]",
                "Alert client [102] that number is now [1]",
                "Alert client [101] that number is now [2]",
                "Alert client [102] that number is now [2]",
                "Alert client [101] that number is now [4]",
                "Alert client [102] that number is now [4]"
        ]

        def executionResult1 = [
                first : [
                        theNumber: 6
                ]
        ]

        def executionResult2 = [
                first : [
                        theNumber: 4
                ]
        ]

        def subRoot = new MutationSchema.SubscriptionRoot(6)

        when:
        def graphql = GraphQL.newGraphQL(MutationSchema.schema).build()
        def execute1 = graphql.execute(subscription1, subRoot)
        graphql.execute(mutation, subRoot)
        def execute2 = graphql.execute(subscription2, subRoot)
        graphql.execute(mutation, subRoot)

        then:
        execute1.data == executionResult1
        execute2.data == executionResult2
        subRoot.result == expectedResult
    }
}
