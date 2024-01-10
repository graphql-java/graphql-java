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
import static graphql.normalized.ExecutableNormalizedOperationToAstCompiler.compileToDocumentWithDeferSupport

class ExecutableNormalizedOperationToAstCompilerDeferTest extends Specification {
    VariablePredicate noVariables = new VariablePredicate() {
        @Override
        boolean shouldMakeVariable(ExecutableNormalizedField executableNormalizedField, String argName, NormalizedInputValue normalizedInputValue) {
            return false
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
            
            type Cat implements Animal {
                name: String
                breed: String
                color: String
                siblings: [Cat]
            }
            
            type Fish implements Animal {
                name: String
            }
            
            type Person {
                firstname: String
                lastname: String
                bestFriend: Person
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
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
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
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
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
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
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
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
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
'''
    }

    def "field with and without defer"() {
        String query = """
          query q {
            dog {
                ... @defer {
                    breed
                }
                ... {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
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
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
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
'''
    }

    def "2 fragments on non-conditional fields"() {
        String query = """
          query q {
            animal {
                ... on Cat @defer {
                    name
                }
                ... on Animal @defer {
                    name
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    ... @defer {
      name
    }
    ... @defer {
      name
    }
  }
}
'''
    }

    def "2 fragments on conditional fields"() {
        String query = """
          query q {
            animal {
                ... on Cat @defer {
                    breed
                }
                ... on Dog @defer {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    ... on Cat @defer {
      breed
    }
    ... on Dog @defer {
      breed
    }
  }
}
'''
    }

    def "2 fragments on conditional fields with different labels"() {
        String query = """
          query q {
            animal {
                ... on Cat @defer(label: "cat-defer") {
                    breed
                }
                ... on Dog @defer(label: "dog-defer") {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    ... on Cat @defer(label: "cat-defer") {
      breed
    }
    ... on Dog @defer(label: "dog-defer") {
      breed
    }
  }
}
'''
    }

    def "fragments on conditional fields with different labels and repeating types"() {
        String query = """
          query q {
            animal {
                ... on Cat @defer(label: "cat-defer-1") {
                    breed
                }
                ... on Cat @defer(label: "cat-defer-2") {
                    breed
                }
                ... on Dog @defer(label: "dog-defer") {
                    breed
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    ... on Cat @defer(label: "cat-defer-1") {
      breed
    }
    ... on Cat @defer(label: "cat-defer-2") {
      breed
    }
    ... on Dog @defer(label: "dog-defer") {
      breed
    }
  }
}
'''
    }

    def "nested defer"() {
        String query = """
          query q {
            animal {
                ... on Cat @defer {
                    name
                }
                ... on Animal @defer {
                    name
                    ... on Dog @defer {
                        owner {
                            firstname
                            ... @defer {
                                lastname
                            }
                            ... @defer {
                                bestFriend {
                                    firstname
                                    ... @defer {
                                        lastname
                                    }
                                }
                            }
                        }
                    }
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  animal {
    ... @defer {
      name
    }
    ... @defer {
      name
    }
    ... on Dog @defer {
      owner {
        firstname
        ... @defer {
          lastname
        }
        ... @defer {
          bestFriend {
            firstname
            ... @defer {
              lastname
            }
          }
        }
      }
    }
  }
}
'''
    }

    def "multiple defers at the same level are preserved"() {
        String query = """
          query q {
            dog {
                ... @defer {
                    name
                }
                ... @defer {
                    breed
                }
                ... @defer {
                    owner {
                        firstname
                    }
                }
            }
          }
        """
        GraphQLSchema schema = mkSchema(sdl)
        def tree = createNormalizedTree(schema, query)
        when:
        def result = compileToDocumentWithDeferSupport(schema, QUERY, null, tree.topLevelFields, noVariables)
        def printed = AstPrinter.printAst(new AstSorter().sort(result.document))
        then:
        printed == '''{
  dog {
    ... @defer {
      name
    }
    ... @defer {
      breed
    }
    ... @defer {
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
        def options = ExecutableNormalizedOperationFactory.Options.defaultOptions().deferSupport(true)
        return dependencyGraph.createExecutableNormalizedOperationWithRawVariables(
                schema,
                originalDocument,
                null,
                RawVariables.of(variables),
                options
        )
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
