package graphql.schema

import spock.lang.Specification

class DelegatingDataFetchingEnvironmentTest extends Specification {

    def "calls to underlying delegate"() {

        def root = "Root"
        def source = "Source"
        def args = [arg1: "val1"]
        def variables = [var1: "varVal1"]

        def dfe = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .source(source)
                .arguments(args)
                .root(root)
                .context("context")
                .variables(variables)
                .build()

        when:
        def delegatingDFE = new DelegatingDataFetchingEnvironment(dfe) {
            @Override
            def getContext() {
                return "overriddenContext"
            }
        }
        then:
        delegatingDFE.getSource() == source
        delegatingDFE.getRoot() == root
        delegatingDFE.getContext() == "overriddenContext"
        delegatingDFE.getVariables() == variables
        delegatingDFE.getArguments() == args
        delegatingDFE.getArgumentOrDefault("arg1", "x") == "val1"
        delegatingDFE.getArgumentOrDefault("arg2", "x") == "x"
    }
}
