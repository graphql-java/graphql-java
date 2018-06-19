package graphql.schema.idl

import graphql.language.EnumTypeDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.ScalarTypeDefinition
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification

/**
 * We don't want to retest the base GraphQL parser since it has its own testing
 * but we do want to test our aspects of it
 */
class SchemaParserTest extends Specification {

    static ALL_DEFINED_TYPES = """

            interface Foo {
               is_foo : Boolean
            }
            
            interface Goo {
               is_goo : Boolean
            }
                 
            type Bar implements Foo {
                is_foo : Boolean
                is_bar : Boolean
            }     

            type Baz implements Foo, Goo {
                is_foo : Boolean
                is_goo : Boolean
                is_baz : Boolean
            }     
            
            enum USER_STATE {
                NOT_FOUND
                ACTIVE
                INACTIVE
                SUSPENDED
            }
            
            scalar Url
            
            type User {
                name : String
                homepage : Url
                state : USER_STATE
            }
            

            type Author {
              id: Int! # the ! means that every author object _must_ have an id
              user: User
              posts: [Post] # the list of Posts by this author
            }
            
            type Post {
              id: Int!
              title: String
              votes: Int
              author: Author
            }
            
            # the schema allows the following query:
            type Query {
              posts: [Post]
              author(id: Int!): Author # author query must receive an id as argument
            }
            
            # this schema allows the following mutation:
            type Mutation {
              upvotePost (
                postId: Int!
              ): Post
            }
            
            extend type Query {
                occurredWhen : String
            }
            
            # we need to tell the server which types represent the root query
            # and root mutation types. We call them RootQuery and RootMutation by convention.
            schema @java(package:"com.company.package", directive2:123) {
              query: Query
              mutation: Mutation
            }
                    
          """

    TypeDefinitionRegistry read(String types) {
        new SchemaParser().parse(types)
    }

    def "test full schema parsing"() {

        def typeRegistry = read(ALL_DEFINED_TYPES)
        def parsedTypes = typeRegistry.types()
        def scalarTypes = typeRegistry.scalars()
        def schemaDef = typeRegistry.schemaDefinition()
        def typeExtensions = typeRegistry.objectTypeExtensions()

        expect:

        parsedTypes.size() == 10

        // some basic checks here.  Other tests are in other places of graphql
        parsedTypes.get("Query") instanceof ObjectTypeDefinition
        parsedTypes.get("Foo") instanceof InterfaceTypeDefinition
        parsedTypes.get("USER_STATE") instanceof EnumTypeDefinition

        typeExtensions.size() == 1
        typeExtensions.get("Query") != null

        scalarTypes.size() == 11 // includes standard scalars
        scalarTypes.get("Url") instanceof ScalarTypeDefinition


        schemaDef.isPresent()
    }

    def "test bad schema"() {
        when:
        def spec = """   
            
            scala Url   # spillin misteak

            interface Foo {
               is_foo : Boolean
            }
            
                    
          """

        read(spec)

        then:
        def schemaProblem = thrown(SchemaProblem)
        schemaProblem.getMessage().contains("InvalidSyntaxError")
        schemaProblem.getErrors().size() == 1
    }

    def "schema with union"() {
        def schema = """     

            type Query {
                foobar: FooOrBar
            }
            
            type Foo {
               name: String 
            }
            
            type Bar {
                other: String
            }
            
            union FooOrBar = Foo | Bar
            
            schema {
              query: Query
            }

        """
        when:
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schema)
        then:
        typeRegistry.types().size() == 4
    }

}
