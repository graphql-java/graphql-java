package graphql.execution.nextgen.depgraph

import graphql.GraphQL
import graphql.TestUtil
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import spock.lang.Specification

class DependencyGraphTest extends Specification {


    def "test"() {
        String schema = """
type Query{ 
    animal: Animal
}
interface Animal {
    name: String
    friends: [Friend]
}

union Pet = Dog | Cat

type Friend {
    name: String
    isBirdOwner: Boolean
    isCatOwner: Boolean
    pets: [Pet] 
}

type Bird implements Animal {
   name: String 
   friends: [Friend]
}

type Cat implements Animal{
   name: String 
   friends: [Friend]
   breed: String 
}

type Dog implements Animal{
   name: String 
   breed: String
   friends: [Friend]
}
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        Document query = new Parser().parseDocument("""
        {
            animal{
                name
               ... on Cat {
                    name
                    friends {
                        ... on Friend {
                            isCatOwner
                        }
                   } 
               }
               ... on Bird {
                    friends {
                        isBirdOwner
                    }
                    friends {
                        name
                    }
               }
               ... on Dog {
                  name   
               }
        }}
        
        """)


        OperationDefinition operationDefinition = (OperationDefinition) query.getDefinitions().get(0);

        DependencyGraph dependencyGraph = new DependencyGraph();
        dependencyGraph.createDependencyGraph(graphQLSchema, operationDefinition)

        expect:
        true

    }

    def "test2"() {
        String schema = """
        type Query{ 
            a: A
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        Document query = new Parser().parseDocument("""
        {
            a {
                ... on A1 {
                   b { 
                     ... on B1 {
                        leaf
                        }
                   }
                }
                ... on A2 {
                    b {
                       ... on B2 {
                            leaf
                       } 
                    }
                }
            }
        }
        
        """)


        OperationDefinition operationDefinition = (OperationDefinition) query.getDefinitions().get(0);

        DependencyGraph dependencyGraph = new DependencyGraph();
        dependencyGraph.createDependencyGraph(graphQLSchema, operationDefinition)

        expect:
        true

    }

    def "test3"() {
        String schema = """
        type Query{ 
            a: [A]
        }
        interface A {
           b: B  
        }
        type A1 implements A {
           b: B 
        }
        type A2 implements A{
            b: B
        }
        interface B {
            leaf: String
        }
        type B1 implements B {
            leaf: String
        } 
        type B2 implements B {
            leaf: String
        } 
    
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            a {
                ... on A1 {
                   b { 
                   ... on B {
                    leaf
                   }
                     ... on B1 {
                        leaf
                        }
                       ... on B2 {
                       ... on B {
                            leaf
                       }
                            leaf
                            leaf
                            ... on B2 {
                                leaf
                            }
                       } 
                   }
                }
            }
        }
        
        """

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();

        assert graphQL.execute(query).errors.size() == 0

        Document document = new Parser().parseDocument(query)
        OperationDefinition operationDefinition = (OperationDefinition) document.getDefinitions().get(0);

        DependencyGraph dependencyGraph = new DependencyGraph();
        dependencyGraph.createDependencyGraph(graphQLSchema, operationDefinition)

        expect:
        true

    }

    def "test4"() {
        String schema = """
        type Query{ 
            pets: [Pet]
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet{
            name: String
        }
        union CatOrDog = Cat | Dog
        """
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

        String query = """
        {
            pets {
                ... on Dog {
                    ... on CatOrDog {
                    ... on Cat{
                            name
                            }
                    }
                }
            }
        }
        
        """

        GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build();

        assert graphQL.execute(query).errors.size() == 0

        Document document = new Parser().parseDocument(query)
        OperationDefinition operationDefinition = (OperationDefinition) document.getDefinitions().get(0);

        DependencyGraph dependencyGraph = new DependencyGraph();
        dependencyGraph.createDependencyGraph(graphQLSchema, operationDefinition)

        expect:
        true

    }
}
