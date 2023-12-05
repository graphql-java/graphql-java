package graphql.normalized

import graphql.GraphQL
import graphql.TestUtil
import graphql.execution.RawVariables
import graphql.language.AstPrinter
import graphql.language.AstSorter
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TestLiveMockedWiringFactory
import graphql.schema.scalars.JsonScalar
import spock.lang.Specification

import static graphql.ExecutionInput.newExecutionInput
import static graphql.language.OperationDefinition.Operation.QUERY
import static graphql.normalized.ExecutableNormalizedOperationToAstCompiler.compileToDocument

class ExecutableNormalizedOperationToAstCompilerDeferTest extends Specification {
    VariablePredicate noVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            return false
        }
    }

    VariablePredicate jsonVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            "JSON" == normalizedInputValue.unwrappedTypeName && normalizedInputValue.value != null
        }
    }

    VariablePredicate allVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            return true
        }
    }

    String sdl = """
            directive @defer(if: Boolean, label: String) on FRAGMENT_SPREAD | INLINE_FRAGMENT

            type Query {
                dog: Dog
                animal: Animal
            }
            
            interface Animal {
                name: String
            }

            type Dog implements Animal {
                name: String
                breed: String
                owner: Person
            }
            
            type Person {
                firstname: String
            }
        """

    def "simple defer"() {
        String query = """
          query q {
            dog {
                name
                ... @defer(label: "breed-defer") {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = compileToDocument(schema, QUERY, null, fields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
    name
    ... @defer(label: "breed-defer") {
      breed
    }
  }
}
'''
    }

    def "simple defer with named spread"() {
        String query = """
          query q {
            dog {
                name
                ... on Dog @defer(label: "breed-defer") {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = compileToDocument(schema, QUERY, null, fields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
    name
    ... @defer(label: "breed-defer") {
      breed
    }
  }
}
'''
    }

    def "multiple labels on the same field"() {
        String query = """
          query q {
            dog {
                name
                ... @defer(label: "breed-defer") {
                    breed
                }
                ... @defer(label: "breed-defer-2") {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = compileToDocument(schema, QUERY, null, fields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
    name
    ... @defer(label: "breed-defer") {
      breed
    }
    ... @defer(label: "breed-defer-2") {
      breed
    }
  }
}
'''
    }

    def "multiple defers with same label on the same field"() {
        String query = """
          query q {
            dog {
                name
                ... @defer(label: "breed-defer") {
                    breed
                }
                ... @defer(label: "breed-defer") {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = compileToDocument(schema, QUERY, null, fields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
    name
    ... @defer(label: "breed-defer") {
      breed
    }
  }
}
'''
    }

    def "multiple defers without label on the same field"() {
        String query = """
          query q {
            dog {
                name
                ... @defer {
                    breed
                }
                ... @defer {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = compileToDocument(schema, QUERY, null, fields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
    name
    ... @defer {
      breed
    }
  }
}
'''
    }

    def "defer on type spread"() {
        String query = """
          query q {
            animal {
              ... on Dog @defer {
                breed
              } 
              ... on Dog {
                name
              } 
              ... on Dog @defer(label: "owner-defer") {
                owner {
                    firstname
                }
              } 
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def fields = createNormalizedFields(schema, query)
        when:
        def result = compileToDocument(schema, QUERY, null, fields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    name
    ... on Dog @defer {
      breed
    }
    ... on Dog @defer(label: "owner-defer") {
      owner {
        firstname
      }
    }
  }
}
'''
    }

    private ExecutableNormalizedOperation createNormalizedTree(GraphQLSchema schema, String query, Map<String, Object> variables = [:]) {
        assertValidQuery(schema, query, variables)
        Document originalDocument = TestUtil.parseQuery(query)

        ExecutableNormalizedOperationFactory dependencyGraph = new ExecutableNormalizedOperationFactory()
        return dependencyGraph.createExecutableNormalizedOperationWithRawVariables(schema, originalDocument, null, RawVariables.of(variables))
    }

    private List<ExecutableNormalizedField> createNormalizedFields(GraphQLSchema schema, String query, Map<String, Object> variables = [:]) {
        return createNormalizedTree(schema, query, variables).getTopLevelFields()
    }

    private void assertValidQuery(GraphQLSchema graphQLSchema, String query, Map variables = [:]) {
        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()
        assert graphQL.execute(newExecutionInput().query(query).variables(variables)).errors.isEmpty()
    }

    GraphQLSchema mkSchema(String sdl) {
        def wiringFactory = new TestLiveMockedWiringFactory([JsonScalar.JSON_SCALAR])
        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory).build()
        TestUtil.schema(sdl, runtimeWiring)
    }
}
