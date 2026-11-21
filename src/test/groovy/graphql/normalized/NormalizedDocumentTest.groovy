package graphql.normalized

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.GraphQLContext
import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.execution.ExecutionContextBuilder
import graphql.execution.MergedField
import graphql.execution.conditional.ConditionalNodeDecision
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import graphql.language.Field
import graphql.language.FragmentSpread
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingFieldSelectionSetImpl
import graphql.validation.QueryComplexityLimits
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class NormalizedDocumentTest extends Specification {
    def sdl = '''
        type Query { pet: Pet box: Box }
        interface Pet { name: String child: Pet }
        type Cat implements Pet { name: String child: Cat lives: Int }
        type Dog implements Pet { name: String child: Dog nickname: String }
        type Box { name: String count(value: Int = 42): Int child: Box }
    '''
    def schema = TestUtil.schema(sdl)

    private static ExecutionInput input(String query, Map variables = [:], boolean enabled = true) {
        ExecutionInput.newExecutionInput(query).variables(variables)
                .graphQLContext([(NormalizedDocument.ENABLED): enabled]).build()
    }

    private static executeOnSmallStack(GraphQL graphQL, ExecutionInput request) {
        def future = new CompletableFuture()
        def thread = new Thread(null, {
            try {
                future.complete(graphQL.execute(request))
            } catch (Throwable failure) {
                future.completeExceptionally(failure)
            }
        } as Runnable, 'normalized-fragment-stack', 1024 * 1024)
        thread.start()
        future.get(30, java.util.concurrent.TimeUnit.SECONDS)
    }

    private static runtimeSchema(String spec, Map<String, Map<String, DataFetcher>> fetchers) {
        def base = TestUtil.schema(spec)
        def registry = graphql.schema.GraphQLCodeRegistry.newCodeRegistry(base.codeRegistry)
        fetchers.each { type, fields ->
            fields.each { name, fetcher -> registry.dataFetcher(graphql.schema.FieldCoordinates.coordinates(type, name), fetcher) }
        }
        base.transform { it.codeRegistry(registry.build()) }
    }

    def "creation is opt-in and failures never expose a normalized document"() {
        expect:
        def result = ParseAndValidate.parseAndValidate(schema, input(query, [:], enabled))
        result.failure == invalid
        (result.normalizedDocument != null) == created

        where:
        query                         | enabled | invalid | created
        '{ box { name } }'            | false   | false   | false
        '{ box { name } }'            | true    | false   | true
        '{ box { missing } }'         | true    | true    | false
        '{ box(unknown: {a: [1]}) { name } }' | true | true | false
        '{ box { name '               | true    | true    | false
        '{ box { ...F } } fragment F on Box { ...F }' | true | true | false
    }

    def "one shared fragment body retains distinct references and symbolic conditions"() {
        given:
        def query = '''
            query A($first: Boolean!, $second: Boolean!) {
                box { ...F @include(if: $first) ...F @include(if: $second) }
            }
            query B { box { ...F } }
            fragment F on Box { x: count(value: 7) child { name } }
        '''

        when:
        def result = ParseAndValidate.parseAndValidate(schema, input(query))
        def document = result.normalizedDocument
        def box = document.fields.values().find { it.source.name == 'box' }
        def scopes = document.selectionSets[box.selectionSetId].selections

        then:
        result.errors.empty
        document.operations*.source*.name == ['A', 'B']
        scopes.size() == 2
        scopes.every { it instanceof NormalizedSelectionScope }
        scopes*.selectionSetId.unique().size() == 1
        scopes*.source*.getDirectives('include')*.first()*.arguments*.first()*.value*.name == ['first', 'second']
        scopes*.source.every { it instanceof FragmentSpread }
        document.fields.size() == 5
        document.fields.values().find { it.source.alias == 'x' }.source.sourceLocation.line == 6
        document.fields.values().find { it.source.alias == 'x' }.parentTypeName == 'Box'

        when:
        document.selectionSets.clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        document.fields.clear()

        then:
        thrown(UnsupportedOperationException)
    }

    def "cached graph evaluates each execution's variables without storing resolved arguments"() {
        given:
        def captures = []
        def runtimeSchema = runtimeSchema(sdl, [Query: [box: { env ->
            captures.add(env.selectionSet.immediateFields.collect { [it.resultKey, it.arguments] })
            null
        } as DataFetcher]])
        def query = '''query($first: Boolean!, $second: Boolean!, $value: Int) {
            box { ...F @include(if: $first) ...F @include(if: $second) }
        } fragment F on Box { count(value: $value) }'''
        PreparsedDocumentEntry cached
        def validations = new AtomicInteger()
        PreparsedDocumentProvider provider = { executionInput, compute ->
            if (cached == null) {
                validations.incrementAndGet()
                cached = compute.apply(executionInput)
            }
            CompletableFuture.completedFuture(cached)
        }
        def graphQL = GraphQL.newGraphQL(runtimeSchema).preparsedDocumentProvider(provider).build()

        expect:
        graphQL.execute(input(query, [first: false, second: true, value: 7])).errors.empty
        graphQL.execute(input(query, [first: true, second: false, value: 9])).errors.empty
        graphQL.execute(input(query, [first: false, second: false, value: 11])).errors.empty
        validations.get() == 1
        cached.normalizedDocument != null
        captures == [[['count', [value: 7]]], [['count', [value: 9]]], []]
    }

    def "lookahead fails clearly without opt in or when a cache drops the graph"() {
        given:
        def runtimeSchema = runtimeSchema(sdl, [Query: [box: { env ->
            env.selectionSet.immediateFields
            null
        } as DataFetcher]])
        PreparsedDocumentProvider provider = { executionInput, compute ->
            def entry = compute.apply(executionInput)
            CompletableFuture.completedFuture(dropGraph ? new PreparsedDocumentEntry(entry.document) : entry)
        }
        def graphQL = GraphQL.newGraphQL(runtimeSchema).preparsedDocumentProvider(provider).build()

        when:
        def result = graphQL.execute(input('{ box { name } }', [:], enabled))

        then:
        result.errors.size() == 1
        result.errors[0].exception instanceof IllegalStateException
        result.errors[0].message.contains('NormalizedDocument.ENABLED')
        result.errors[0].message.contains('preparsed cache')

        where:
        enabled | dropGraph
        false   | false
        true    | true
    }

    def "every inspection including leaf and empty pattern requires the artifact"() {
        given:
        def context = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(graphql.execution.ExecutionId.generate())
                .graphQLSchema(schema).graphQLContext(GraphQLContext.getDefault()).build()
        def selection = DataFetchingFieldSelectionSetImpl.newCollector(context,
                MergedField.newMergedField(Field.newField('name').build()).build(), graphql.Scalars.GraphQLString, 1)

        when:
        inspection(selection)

        then:
        thrown(IllegalStateException)

        where:
        inspection << [
                { it.immediateFields }, { it.fields }, { it.contains('') }, { it.getFields('') },
                { it.containsAnyOf('name') }, { it.containsAllOf('name') },
                { it.fieldsGroupedByResultKey }, { it.getFieldsGroupedByResultKey('name') }
        ]
    }

    def "concrete type variants are separate and children are collected on demand"() {
        given:
        def captured
        def runtimeSchema = runtimeSchema(sdl, [Query: [pet: { env -> captured = env.selectionSet; null } as DataFetcher]])
        def graphQL = GraphQL.newGraphQL(runtimeSchema).build()

        when:
        def result = graphQL.execute(input('''{ pet {
            name
            ... on Cat { label: name child { name } child { lives } }
            ... on Dog { label: nickname child { nickname } }
        } }'''))

        then:
        result.errors.empty
        captured.immediateFields*.fullyQualifiedName == ['Cat.name', 'Cat.name', 'Cat.child', 'Dog.name', 'Dog.nickname', 'Dog.child']
        captured.immediateFields*.resultKey == ['name', 'label', 'child', 'name', 'label', 'child']
        captured.immediateFields*.objectTypeNames == [['Cat'], ['Cat'], ['Cat'], ['Dog'], ['Dog'], ['Dog']]
        captured.immediateFields.every { it.conditional && it.level == 2 && it.parentField == null }
        def catChild = captured.immediateFields[2]
        catChild.selectionSet.immediateFields*.fullyQualifiedName == ['Cat.name', 'Cat.lives']
        catChild.selectionSet.immediateFields.every { !it.conditional && it.level == 3 && it.parentField.is(catChild) }
        captured.getFields('Cat.child/*')*.fullyQualifiedName == ['Cat.child/Cat.name', 'Cat.child/Cat.lives']
        captured.getFields('Dog.child/*')*.fullyQualifiedName == ['Dog.child/Dog.nickname']
        captured.getFieldsGroupedByResultKey('name')['name'].size() == 2
    }

    def "nested scope conditions combine and aliases retain first active order"() {
        given:
        def keys
        def runtimeSchema = runtimeSchema(sdl, [Query: [box: { env -> keys = env.selectionSet.immediateFields*.resultKey; null } as DataFetcher]])
        def graphQL = GraphQL.newGraphQL(runtimeSchema).build()
        def query = '''query($outer: Boolean!, $inner: Boolean!) {
            box {
                ... @include(if: $outer) { ...F @include(if: $inner) }
                last: name
                ...F
            }
        } fragment F on Box { first: name }'''

        expect:
        graphQL.execute(input(query, [outer: outer, inner: inner])).errors.empty
        keys == expected

        where:
        outer | inner | expected
        true  | true  | ['first', 'last']
        false | true  | ['last', 'first']
        true  | false | ['last', 'first']
    }

    def "custom conditional decisions are applied to scopes definitions and fields"() {
        given:
        def fields
        def runtimeSchema = runtimeSchema(sdl + '\ndirective @hide on FIELD | FRAGMENT_SPREAD | FRAGMENT_DEFINITION | INLINE_FRAGMENT',
                [Query: [box: { env -> fields = env.selectionSet.immediateFields*.name; null } as DataFetcher]])
        def graphQL = GraphQL.newGraphQL(runtimeSchema).build()
        def query = '''{ box { name ...F @hide ...G ... @hide { count } child @hide { name } } }
            fragment F on Box { count }
            fragment G on Box @hide { child { name } }'''
        def executionInput = input(query)
        executionInput.graphQLContext.put(ConditionalNodeDecision, { env ->
            env.directivesContainer.getDirectives('hide').empty
        } as ConditionalNodeDecision)

        expect:
        graphQL.execute(executionInput).errors.empty
        fields == ['name']
    }

    def "preparsed entries survive serialization and reject mismatched documents"() {
        given:
        def result = ParseAndValidate.parseAndValidate(schema, input('{ box { name } }'))
        def entry = new PreparsedDocumentEntry(result.document, result.errors, result.normalizedDocument)
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(entry) }

        when:
        def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable { it.readObject() }

        then:
        restored.document.is(restored.normalizedDocument.document)
        restored.normalizedDocument.fields.size() == 2
        restored.normalizedDocument.fields.every { ast, field -> ast.is(field.source) }

        when:
        new PreparsedDocumentEntry(TestUtil.parseQuery('{ box { name } }'), [], result.normalizedDocument)

        then:
        thrown(graphql.AssertException)
    }

    def "long fragment chains are collected iteratively and diamond references stay shared"() {
        given:
        def chain = (0..<1501).collect { index ->
            "fragment F$index on Box { " + (index == 1500 ? 'name' : "...F${index + 1}") + ' }'
        }.join('\n')
        def captured
        def runtimeSchema = runtimeSchema(sdl, [Query: [box: { env -> captured = env.selectionSet.immediateFields*.name; null } as DataFetcher]])
        def graphQL = GraphQL.newGraphQL(runtimeSchema).build()

        expect:
        executeOnSmallStack(graphQL, input('{ box { ...F0 } }\n' + chain)).errors.empty
        captured == ['name']

        when:
        def diamond = (0..<24).collect { index ->
            "fragment F$index on Box { " + (index == 23 ? 'name' : "...F${index + 1} ...F${index + 1}") + ' }'
        }.join('\n')
        def request = input('{ box { ...F0 } }\n' + diamond)
        request.graphQLContext.put(QueryComplexityLimits.KEY, QueryComplexityLimits.NONE)
        def result = ParseAndValidate.parseAndValidate(runtimeSchema, request)

        then:
        result.errors.empty
        result.normalizedDocument.selectionSets.size() == 26
        result.normalizedDocument.fields.size() == 2
        graphQL.execute(request).errors.empty
        captured == ['name']
    }
    def "argument defaults follow each concrete definition and distinguish missing from null"() {
        given:
        def spec = """
            type Query { pet: Pet }
            interface Pet { count(value: Int): Int }
            type Cat implements Pet { count(value: Int = 1): Int }
            type Dog implements Pet { count(value: Int = 2): Int }
        """
        def captured
        def runtime = runtimeSchema(spec, [Query: [pet: { env ->
            captured = env.selectionSet.immediateFields.collectEntries { [(it.objectTypeNames[0]): it.arguments] }
            null
        } as DataFetcher]])
        def graphQL = GraphQL.newGraphQL(runtime).build()

        expect:
        graphQL.execute(input('query($value: Int) { pet { count(value: $value) } }', variables)).errors.empty
        captured == expected

        where:
        variables       | expected
        [:]             | [Cat: [value: 1], Dog: [value: 2]]
        [value: null]   | [Cat: [value: null], Dog: [value: null]]
        [value: 7]      | [Cat: [value: 7], Dog: [value: 7]]
    }

    def "one cached artifact is safe across concurrent executions and disabled requests"() {
        given:
        def runtime = runtimeSchema(sdl, [Query: [box: { env ->
            def selected = env.selectionSet.immediateFields
            [count: selected.find { it.name == 'count' }.arguments.value, name: 'visible']
        } as DataFetcher]])
        def query = 'query($value: Int!, $visible: Boolean!) { box { count(value: $value) name @include(if: $visible) } }'
        def prepared = ParseAndValidate.parseAndValidate(runtime, input(query))
        def entry = new PreparsedDocumentEntry(prepared.document, prepared.errors, prepared.normalizedDocument)
        PreparsedDocumentProvider provider = { request, compute -> CompletableFuture.completedFuture(entry) }
        def graphQL = GraphQL.newGraphQL(runtime).preparsedDocumentProvider(provider).build()

        when:
        def pending = (1..20).collect { index ->
            CompletableFuture.supplyAsync {
                graphQL.execute(input(query, [value: index, visible: index % 2 == 0]))
            }
        }
        def results = pending.collect { it.join() }

        then:
        results.every { it.errors.empty }
        results*.data*.box*.count == (1..20).toList()
        results.withIndex().every { result, index -> result.data.box.containsKey('name') == ((index + 1) % 2 == 0) }

        when:
        def disabled = graphQL.execute(input(query, [value: 1, visible: true], false))

        then:
        disabled.errors[0].exception instanceof IllegalStateException
    }

    def "all operations are prepared and the selected operation supplies variable defaults"() {
        given:
        def observed
        def runtime = runtimeSchema(sdl, [Query: [box: { env ->
            observed = env.selectionSet.immediateFields*.arguments
            null
        } as DataFetcher]])
        def query = """
            query A(\$value: Int = 1) { box { ...F } }
            query B(\$value: Int = 2) { box { ...F } }
            fragment F on Box { count(value: \$value) }
        """
        def graphQL = GraphQL.newGraphQL(runtime).build()

        expect:
        graphQL.execute(input(query).transform { it.operationName(operation) }).errors.empty
        observed == [[value: expected]]

        where:
        operation | expected
        'A'       | 1
        'B'       | 2
    }

    def "transformed execution contexts retain the prepared graph"() {
        given:
        def request = input('{ box { name } }')
        def prepared = ParseAndValidate.parseAndValidate(schema, request)
        def context = ExecutionContextBuilder.newExecutionContextBuilder()
                .executionId(graphql.execution.ExecutionId.generate()).graphQLSchema(schema)
                .graphQLContext(request.graphQLContext).document(prepared.document)
                .normalizedDocument(prepared.normalizedDocument).build()

        expect:
        context.transform { it.localContext('event') }.normalizedDocument.is(prepared.normalizedDocument)
    }

    def "complexity rejection discards the optional graph"() {
        given:
        def request = input('{ box { name } }')
        request.graphQLContext.put(QueryComplexityLimits.KEY,
                QueryComplexityLimits.newLimitsWithDefaults().maxMergedFieldCount(1).build())

        when:
        def result = ParseAndValidate.parseAndValidate(schema, request)

        then:
        result.errors.size() == 1
        result.normalizedDocument == null
    }

    def "preparsed entries reject an artifact combined with validation errors"() {
        given:
        def prepared = ParseAndValidate.parseAndValidate(schema, input('{ box { name } }'))
        def error = graphql.GraphqlErrorBuilder.newError().message('invalid').build()

        when:
        new PreparsedDocumentEntry(prepared.document, [error], prepared.normalizedDocument)

        then:
        thrown(graphql.AssertException)
    }

}
