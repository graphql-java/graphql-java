package graphql.introspection

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.execution.CoercedVariables
import graphql.language.Document
import graphql.normalized.ExecutableNormalizedOperationFactory
import graphql.validation.OperationValidationRule
import graphql.validation.QueryComplexityLimits
import spock.lang.Specification

class GoodFaithIntrospectionTest extends Specification {

    def graphql = TestUtil.graphQL("type Query { normalField : String }").build()

    def setup() {
        GoodFaithIntrospection.enabledJvmWide(true)
    }

    def cleanup() {
        GoodFaithIntrospection.enabledJvmWide(true)
    }

    def "standard introspection query is inside limits just in general"() {

        when:
        Document document = TestUtil.toDocument(IntrospectionQuery.INTROSPECTION_QUERY)
        def eno = ExecutableNormalizedOperationFactory.createExecutableNormalizedOperation(graphql.getGraphQLSchema(), document,
                "IntrospectionQuery", CoercedVariables.emptyVariables())

        then:
        eno.getOperationFieldCount() < GoodFaithIntrospection.GOOD_FAITH_MAX_FIELDS_COUNT  // currently 189
        eno.getOperationDepth() < GoodFaithIntrospection.GOOD_FAITH_MAX_DEPTH_COUNT  // currently 13
    }

    def "test asking for introspection in good faith"() {

        when:
        ExecutionResult er = graphql.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        then:
        er.errors.isEmpty()
    }

    def "test asking for introspection in bad faith"() {

        when:
        ExecutionResult er = graphql.execute(query)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError

        where:
        query                                                                                                    | _
        // long attack
        """
        query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}
        """                                                                                           | _
        // a case for __Type interfaces
        """ query badActor {
                __schema { types { interfaces { fields { type { interfaces { name } } } } } }
            }
        """                                                                                           | _
        // a case for __Type inputFields
        """ query badActor {
                __schema { types { inputFields { type { inputFields { name }}}}}
            }
        """                                                                                           | _
        // a case for __Type possibleTypes
        """ query badActor {
                __schema { types { inputFields { type { inputFields { name }}}}}
            }
        """                                                                                           | _
        // a case leading from __InputValue
        """ query badActor {
                __schema { types { fields { args { type { name fields { name }}}}}}
            }
        """                                                                                           | _
        // a case leading from __Field
        """ query badActor {
                __schema { types { fields { type { name fields { name }}}}}
            }
        """                                                                                           | _
        // a case for __type
        """ query badActor {
                __type(name : "t") { name }
                alias1 :  __type(name : "t1") { name }
            }
        """                                                                                           | _
        // a case for __type with aliases
        """ query badActor {
                a1: __type(name : "t") { name }
                a2 :  __type(name : "t1") { name }
            }
        """                                                                                           | _
        // a case for schema repeated - dont ask twice
        """ query badActor {
                __schema { types { name} }
                alias1 : __schema { types { name} }
            }
        """                                                                                           | _
        // a case for used aliases
        """ query badActor {
                a1: __schema { types { name} }
                a2 : __schema { types { name} }
            }
        """                                                                                           | _

    }

    def "mixed general queries and introspections will be stopped anyway"() {
        def query = """
            query goodAndBad {
                normalField
                __schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}
            }
        """

        when:
        ExecutionResult er = graphql.execute(query)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
        er.data == null // it stopped hard - it did not continue to normal business
    }

    def "can be disabled"() {
        when:
        def currentState = GoodFaithIntrospection.isEnabledJvmWide()

        then:
        currentState

        when:
        def prevState = GoodFaithIntrospection.enabledJvmWide(false)

        then:
        prevState

        when:
        ExecutionResult er = graphql.execute("query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}")

        then:
        er.errors.isEmpty()
    }

    def "disabling good faith composes with custom validation rule predicates"() {
        given:
        // Custom predicate that disables a specific rule
        def customPredicate = { OperationValidationRule rule -> rule != OperationValidationRule.KNOWN_ARGUMENT_NAMES } as java.util.function.Predicate

        when:
        def context = [
                (GoodFaithIntrospection.GOOD_FAITH_INTROSPECTION_DISABLED)    : true,
                (ParseAndValidate.INTERNAL_VALIDATION_PREDICATE_HINT)         : customPredicate
        ]
        ExecutionInput executionInput = ExecutionInput.newExecutionInput("{ normalField }")
                .graphQLContext(context).build()
        ExecutionResult er = graphql.execute(executionInput)

        then:
        er.errors.isEmpty()
    }

    def "can be disabled per request"() {
        when:
        def context = [(GoodFaithIntrospection.GOOD_FAITH_INTROSPECTION_DISABLED): true]
        ExecutionInput executionInput = ExecutionInput.newExecutionInput("query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}")
                .graphQLContext(context).build()
        ExecutionResult er = graphql.execute(executionInput)

        then:
        er.errors.isEmpty()

        when:
        context = [(GoodFaithIntrospection.GOOD_FAITH_INTROSPECTION_DISABLED): false]
        executionInput = ExecutionInput.newExecutionInput("query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}")
                .graphQLContext(context).build()
        er = graphql.execute(executionInput)

        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
    }

    def "can stop deep queries"() {

        when:
        def query = createDeepQuery(depth)
        def then = System.currentTimeMillis()
        ExecutionResult er = graphql.execute(query)
        def ms = System.currentTimeMillis() - then

        then:
        !er.errors.isEmpty()
        er.errors[0].class == targetError
        er.data == null // it stopped hard - it did not continue to normal business
        println "Took " + ms + "ms"

        where:
        depth | targetError
        2     | GoodFaithIntrospection.BadFaithIntrospectionError.class
        10  | GoodFaithIntrospection.BadFaithIntrospectionError.class
        15  | GoodFaithIntrospection.BadFaithIntrospectionError.class
        20  | GoodFaithIntrospection.BadFaithIntrospectionError.class
        25  | GoodFaithIntrospection.BadFaithIntrospectionError.class
        50  | GoodFaithIntrospection.BadFaithIntrospectionError.class
        100 | GoodFaithIntrospection.BadFaithIntrospectionError.class
    }

    def "introspection via inline fragment on Query is detected as bad faith"() {
        def query = """
            query badActor {
                ...on Query {
                    __schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}
                }
            }
        """

        when:
        ExecutionResult er = graphql.execute(query)

        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
    }

    def "introspection via fragment spread is detected as bad faith"() {
        def query = """
            query badActor {
                ...IntrospectionFragment
            }
            fragment IntrospectionFragment on Query {
                __schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}
            }
        """

        when:
        ExecutionResult er = graphql.execute(query)

        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
    }

    def "good faith limits are applied on top of custom user limits"() {
        given:
        def limits = QueryComplexityLimits.newLimits().maxFieldsCount(200).maxDepth(15).build()
        def executionInput = ExecutionInput.newExecutionInput(IntrospectionQuery.INTROSPECTION_QUERY)
                .graphQLContext([(QueryComplexityLimits.KEY): limits])
                .build()

        when:
        ExecutionResult er = graphql.execute(executionInput)

        then:
        er.errors.isEmpty()
    }

    def "introspection query exceeding field count limit is detected as bad faith"() {
        given:
        // Build a wide introspection query that exceeds GOOD_FAITH_MAX_FIELDS_COUNT (500)
        // using non-cycle-forming fields (aliases of 'name') so the tooManyFields check
        // does not fire first, exercising the tooBigOperation code path instead
        def sb = new StringBuilder()
        sb.append("query { __schema { types { ")
        for (int i = 0; i < 510; i++) {
            sb.append("a${i}: name ")
        }
        sb.append("} } }")

        when:
        ExecutionResult er = graphql.execute(sb.toString())

        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
        er.errors[0].message.contains("too big")
    }

    def "introspection query exceeding depth limit is detected as bad faith"() {
        given:
        // Build a deep introspection query using ofType (not a cycle-forming field)
        // that exceeds GOOD_FAITH_MAX_DEPTH_COUNT (20)
        def sb = new StringBuilder()
        sb.append("query { __schema { types { ")
        for (int i = 0; i < 20; i++) {
            sb.append("ofType { ")
        }
        sb.append("name ")
        for (int i = 0; i < 20; i++) {
            sb.append("} ")
        }
        sb.append("} } }")

        when:
        ExecutionResult er = graphql.execute(sb.toString())

        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospection.BadFaithIntrospectionError
        er.errors[0].message.contains("too big")
    }

    String createDeepQuery(int depth = 25) {
        def result = """
query test {
  __schema {
    types {
      ...F1
    }
  }
}
"""
        for (int i = 1; i < depth; i++) {
            result += """
        fragment F$i on __Type {
          fields {
            type {
              ...F${i + 1}
            }
          }

  ofType {
    ...F${i + 1}
  }
}


"""
        }
        result += """
        fragment F$depth on __Type {
          fields {
            type {
name
            }
          }
}


"""
        return result
    }
}
