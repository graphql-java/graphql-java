package graphql.execution.instrumentation.export

import graphql.GraphQL
import graphql.StarWarsSchema
import spock.lang.Specification

class ExportVariablesInstrumentationTest extends Specification {

    def "exported variables are captured"() {

        def exportVariablesInstrumentation = new ExportVariablesInstrumentation()

        def graphQL = GraphQL.newGraphQL(StarWarsSchema.starWarsSchema)
                .instrumentation(exportVariablesInstrumentation)
                .build()

        given:
        def executionResult = graphQL.execute("""
            query {
                hero 
                {
                    id @export(as:"heroId")
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

        Map<String, List<Object>> exportedVariables = exportVariablesInstrumentation.getExportedVariables()

        exportedVariables.size() == 3
        exportedVariables['heroId'] == ["2001"]
        exportedVariables['r2d2Friends'][0] == [
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
}
