package graphql

import graphql.schema.DataFetcher
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput

class DefaultValuesTest extends Specification {

    def "provided values override defaults"() {
        def sdl = """
        type Query {
            myField(deleted: Boolean = true) : String
            myField2(deleted: Boolean = true) : String
        }
        """

        def df = { env ->
            return "dataFetcherArg=" + env.getArgument("deleted")
        } as DataFetcher
        def graphQL = TestUtil.graphQL(sdl, [Query: [
                myField : df,
                myField2: df]]
        ).build()

        //
        // The variable is present in the variables map and its explicitly null
        //
        // https://spec.graphql.org/draft/#sec-Coercing-Variable-Values
        //
        when:
        def ei = newExecutionInput('''
            query myQuery($deleted: Boolean = false) {
                myField(deleted : $deleted)
            }
        ''').variables(["deleted": null]).build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [myField: "dataFetcherArg=null"]

        //
        // The variable is present in the variables map and its explicitly a value
        //
        // https://spec.graphql.org/draft/#sec-Coercing-Variable-Values
        //
        when:
        ei = newExecutionInput('''
            query myQuery($deleted: Boolean = false) {
                myField(deleted : $deleted)
            }
        ''').variables(["deleted": true]).build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [myField: "dataFetcherArg=true"]

        //
        // The variable is NOT present in the variables map it should use a default
        // value from the variable declaration
        //
        // https://spec.graphql.org/draft/#sec-Coercing-Variable-Values
        //
        when:
        ei = newExecutionInput('''
            query myQuery($deleted: Boolean = false) {
                myField(deleted : $deleted)
            }
        ''').variables(["NotProvided": "valueNotProvided"]).build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [myField: "dataFetcherArg=false"]

        //
        // The variable is NOT present in the variables map and a variable is NOT used
        // it should use a default value from the field declaration
        //
        //
        when:
        ei = newExecutionInput('''
            query myQuery($deleted: Boolean = false) {
                myField
                myField2(deleted : $deleted)
            }
        ''').variables(["NotProvided": "valueNotProvided"]).build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [myField : "dataFetcherArg=true",
                    myField2: "dataFetcherArg=false"]

        //
        // If there are no variables on the query operation
        // it should use a default value from the field declaration
        // or literals provided
        //
        when:
        ei = newExecutionInput('''
            query myQuery {
                myField(deleted :false)
                myField2
            }
        ''').variables(["NotProvided": "valueNotProvided"]).build()
        er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == [myField : "dataFetcherArg=false",
                    myField2: "dataFetcherArg=true"]
    }
}
