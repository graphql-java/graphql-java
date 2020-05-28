package graphql

import graphql.execution.DataFetcherResult
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import spock.lang.Specification

class LocalContextTest extends Specification {

    // Configuration for the datafetchers for this schema, this will build a local context which will be consumed all the way down to the user
    // of the comments
    def graphql = TestUtil.graphQL("""
                type Query {
                    blog(arg: String): Blog!
                }
                
                type Blog {
                    name: String!
                    echoLocalContext: String
                    comments: [Comment!]!
                }
                
                type Comment {
                    author: User!
                    title: String!
                    body: String!
                    
                }
                
                type User {
                    username: String!
                }
            """,
            RuntimeWiring.newRuntimeWiring()
                    .type(TypeRuntimeWiring.newTypeWiring("Query")
                            .dataFetcher("blog", {
                                def blog = new Blog()
                                blog.name = "some name"
                                if (it.getArgument("arg") != null) {
                                    DataFetcherResult.newResult()
                                            .data("foo")
                                            .localContext(it.getArgument("arg")) // here is where we build the initial context
                                            .build()
                                } else {
                                    DataFetcherResult.newResult()
                                            .data(blog)
                                            .localContext("BLOG CONTEXT") // here is where we build the initial context
                                            .build()
                                }
                            })
                    )
                    .type(TypeRuntimeWiring.newTypeWiring("Blog")
                            .dataFetcher("echoLocalContext", {
                                return it.getLocalContext()
                            })
                            .dataFetcher("comments", {
                                def comment = new Comment()
                                comment.title = it.getLocalContext() + " (comments data fetcher)"
                                DataFetcherResult.newResult()
                                        .data([comment])
                                        .build()
                            })
                    )
                    .type(TypeRuntimeWiring.newTypeWiring("Comment")
                            .dataFetcher("body", { it.getLocalContext() + " (comment data fetcher)" })
                            .dataFetcher("author", { new User() })
                    )
                    .type(TypeRuntimeWiring.newTypeWiring("User")
                            .dataFetcher("username", { it.getLocalContext() + " (user data fetcher)" })
                    )
                    .build())
            .build()

    def "when a local context is provided, it can be consumed by the grandchildren node data fetchers"() {
        given:
        def input = ExecutionInput.newExecutionInput()
                .query('''
                            query {
                                blog {
                                    name
                                    comments {
                                        title
                                        body
                                        author {
                                            username
                                        }
                                    }
                                }
                            }
                            ''')
                .build()
        when:
        def executionResult = graphql.execute(input)

        then:
        executionResult.errors.isEmpty()
        executionResult.data == [
                blog: [
                        name    : "some name",
                        comments: [
                                [
                                        title : "BLOG CONTEXT (comments data fetcher)",
                                        body  : "BLOG CONTEXT (comment data fetcher)",
                                        author: [
                                                username: "BLOG CONTEXT (user data fetcher)"
                                        ]
                                ]
                        ]
                ]
        ]
    }

    def "different alias produce different local contexts"() {
        given:
        def input = ExecutionInput.newExecutionInput()
                .query('''
                            query {
                                blog1: blog(arg: "blog1") {
                                    echoLocalContext
                                }
                                blog2: blog(arg: "blog2") {
                                    echoLocalContext
                                }
                            }
                            ''')
                .build()
        when:
        def executionResult = graphql.execute(input)

        then:
        executionResult.errors.isEmpty()
        executionResult.data == [
                blog1: [
                        echoLocalContext: "blog1"
                ],
                blog2: [
                        echoLocalContext: "blog2"
                ]
        ]
    }

    // Really simply beans, without getters and setters for simplicity
    static class User {
        public String username
    }

    static class Comment {
        String title
        String body
        User author
    }

    static class Blog {
        String name
        List<Comment> comments
    }
}
