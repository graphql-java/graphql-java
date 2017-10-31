package graphql.execution.instrumentation.export

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.StarWarsSchema
import spock.lang.Specification

class ExportedVariablesInstrumentationTest extends Specification {

    def "exported variables are captured via plural names"() {

        def collector = new PluralExportedVariablesCollector()
        def exportVariablesInstrumentation = new ExportedVariablesInstrumentation({ -> collector })

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(exportVariablesInstrumentation)
                .build()

        given:
        def executionResult = graphQL.execute("""
            query {
                hero 
                {
                    id @export(as:"droidId")
                    name 
                    friends  @export(as:"r2d2Friends") 
                    {
                        name @export(as:"friendNames")
                    }
                }
            }
        """)

        expect:
        executionResult.getErrors().size() == 0

        Map<String, Object> exportedVariables = collector.getVariables()

        exportedVariables.size() == 3
        exportedVariables['droidId'] == "2001"
        exportedVariables['r2d2Friends'] == [
                [name: "Luke Skywalker"],
                [name: "Han Solo"],
                [name: "Leia Organa"],
        ]
        exportedVariables['friendNames'] == [
                "Luke Skywalker",
                "Han Solo",
                "Leia Organa",
        ]
    }

    def "exported variables feed into future queries"() {

        def collector = new PluralExportedVariablesCollector()
        def exportVariablesInstrumentation = new ExportedVariablesInstrumentation({ -> collector })

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(exportVariablesInstrumentation)
                .build()

        def query = '''
            query A {
                hero 
                {
                    id @export(as:"droidId")
                    name 
                    friends  @export(as:"r2d2Friends") 
                    {
                        name @export(as:"friendNames")
                    }
                }
            }
            
            query B($droidId : String!) {
                droid (id : $droidId ) {
                    name
                }
            }
        '''
        given:

        ExecutionInput input = ExecutionInput.newExecutionInput().query(query).operationNames(["A","B"]).build()

        def executionResult = graphQL.execute(input)

        expect:
        executionResult.getErrors().size() == 0

        executionResult.data["B"] == [
                droid: [
                        name: "R2-D2"
                ]
        ]
    }

    def "lists are handled as expected"() {
        def collector = new PluralExportedVariablesCollector()
        def exportVariablesInstrumentation = new ExportedVariablesInstrumentation({ -> collector })

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(exportVariablesInstrumentation)
                .build()

        def query = '''
            query A {
                hero 
                {
                    id @export(as:"droidId")
                    name 
                    friends   
                    {
                        id @export(as:"r2d2Friends")
                    }
                }
            }
            
            query B ($r2d2Friends : [String]!) {
                humans (ids : $r2d2Friends ) {
                    name
                }
            }
        '''
        given:

        ExecutionInput input = ExecutionInput.newExecutionInput().query(query).operationNames(["A","B"]).build()

        def executionResult = graphQL.execute(input)

        expect:
        executionResult.getErrors().size() == 0

        executionResult.data["B"] == [
                humans: [[name:"Luke Skywalker"], [name:"Han Solo"], [name:"Leia Organa"]]
        ]
    }

    def "mutations work as expected"() {
        def collector = new PluralExportedVariablesCollector()
        def exportVariablesInstrumentation = new ExportedVariablesInstrumentation({ -> collector })

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(exportVariablesInstrumentation)
                .build()

        def query = '''
            query A {
                hero 
                {
                    id @export(as:"droidId")
                    name 
                    friends   
                    {
                        id @export(as:"r2d2Friends")
                    }
                }
            }
            
            mutation B ($r2d2Friends : [String]!) {
                forceChoke (ids : $r2d2Friends ) {
                    name
                }
            }
        '''
        given:

        ExecutionInput input = ExecutionInput.newExecutionInput().query(query).operationNames(["A","B"]).build()

        def executionResult = graphQL.execute(input)

        expect:
        executionResult.getErrors().size() == 0

        executionResult.data["B"] == [
                forceChoke: [[name:"Luke Skywalker"], [name:"Han Solo"], [name:"Leia Organa"]]
        ]
    }

}
