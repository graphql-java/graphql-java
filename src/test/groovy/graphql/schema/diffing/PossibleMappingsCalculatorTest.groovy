package graphql.schema.diffing


import spock.lang.Specification

import static graphql.TestUtil.schema

class PossibleMappingsCalculatorTest extends Specification {


    def "no diff schema"() {
        given:
        def sourceGraph = sourceGraph('''   
    type Query {
      hello: String
    }
''')
        def targetGraph = targetGraph('''
    type Query {
      hello: String
    }
''')
        def calculator = createCalculator(sourceGraph, targetGraph)

        when:
        def mappings = calculator.calculateInitialMapping()

        then:
        mappings.fixedOneToOneMappings.size() == sourceGraph.size()
        mappings.fixedOneToOneMappings.size() == targetGraph.size()
    }

    def "two fields in source, three in target"() {
        given:
        def sourceGraph = sourceGraph('''   
    type Query {
      hello: String
      hello1: String
    }
''')
        def targetGraph = targetGraph('''
    type Query {
      hello2: String
      hello3: String
      hello4: String
    }
''')
        def calculator = createCalculator(sourceGraph, targetGraph)
        def helloField = sourceGraph.getVerticesByType(SchemaGraph.FIELD).find { it.name == "hello" }

        when:
        def mappings = calculator.calculateInitialMapping()
        def isolatedFieldVertex = sourceGraph.getVerticesByType(SchemaGraph.ISOLATED).find { it.debugName.startsWith("source-isolated-Field-") }

        then:
        mappings.possibleTargets(helloField).size() == 4
        mappings.possibleTargets(helloField).findAll { it.isIsolated() }.size() == 1
        mappings.possibleTargets(helloField).findAll { it.isOfType(SchemaGraph.FIELD) }.size() == 3

        mappings.possibleTargets(isolatedFieldVertex).size() == 3
        mappings.possibleTargets(isolatedFieldVertex).findAll { it.isOfType(SchemaGraph.FIELD) }.size() == 1
        mappings.possibleTargets(isolatedFieldVertex).findAll { it.isIsolated() }.size() == 2

    }

    def "initial field one to one mapping"() {
        given:
        def sourceGraph = sourceGraph('''   
    type Query {
      foo(arg: String): String
    }
''')
        def targetGraph = targetGraph('''
    type Query {
        fooRenamed: Foo
    }
    type Foo {
        a(arg2: String): String
        b(arg3: String): String
    }
''')
        def calculator = createCalculator(sourceGraph, targetGraph)
        def fooField = sourceGraph.getVertices().find { it.name == "foo" }
        def argArgument = sourceGraph.getArgumentsForField(fooField)[0]
        when:
        def mappings = calculator.calculateInitialMapping()

        then:
        // only one possible mapping for the argument: isolated
        mappings.possibleMappings.get(argArgument).size() == 1
        mappings.possibleMappings.get(argArgument).findAll { it.isolated }.size() == 1

    }


    SchemaGraph sourceGraph(String sdl) {
        def schema = schema(sdl)
        def sourceGraph = new SchemaGraphFactory("source-").createGraph(schema);
        return sourceGraph
    }

    SchemaGraph targetGraph(String sdl) {
        def schema = schema(sdl)
        def targetGraph = new SchemaGraphFactory("target-").createGraph(schema);
        return targetGraph
    }


    PossibleMappingsCalculator createCalculator(SchemaGraph sourceGraph, SchemaGraph targetGraph) {
        def runningCheck = new SchemaDiffingRunningCheck()
        return new PossibleMappingsCalculator(sourceGraph, targetGraph, runningCheck)
    }
}
