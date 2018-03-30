package graphql.cats

import graphql.cats.runner.ScenarioLoader
import graphql.cats.runner.ScenarioTestRunner
import graphql.cats.runner.TestResult
import spock.lang.Specification
import spock.lang.Unroll

class GraphqlCatsTckTests extends Specification {

    @Unroll
    def "Cats TCK Parsing  '#testName'"() {
        expect:

        if (! result.passed) {
            assert false, result.reason + " : '" + result.query + "'"
        }

        where:
        result << cats("cats/scenarios/parsing/SchemaParser.yaml")
        testName = result.testName
    }

    List<TestResult> cats(String catsYamlFile) {
        def scenario = new ScenarioLoader().load(catsYamlFile)
        def testResults = new ScenarioTestRunner().runScenario(scenario)
        testResults
    }
}
