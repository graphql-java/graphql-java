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
