package graphql.schema.idl

import graphql.schema.*
import spock.lang.Specification

class SchemaGeneratorTest extends Specification {


    GraphQLSchema generateSchema(String schemaSpec, RuntimeWiring wiring) {
        def typeRegistry = new SchemaCompiler().compile(schemaSpec)
        def result = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring)
        result
    }

    GraphQLType unwrap1Layer(GraphQLType type) {
        if (type instanceof GraphQLNonNull) {
            type = (type as GraphQLNonNull).wrappedType
        } else if (type instanceof GraphQLList) {
            type = (type as GraphQLList).wrappedType
        }
        type
    }

    GraphQLType unwrap(GraphQLType type) {
        while (true) {
            if (type instanceof GraphQLNonNull) {
                type = (type as GraphQLNonNull).wrappedType
            } else if (type instanceof GraphQLList) {
                type = (type as GraphQLList).wrappedType
            } else {
                break
            }
        }
        type
    }


    def "test simple schema generate"() {

        def schemaSpec = """
            type Author {
                # the ! means that every author object _must_ have an id
              id: Int! 
              firstName: String
              lastName: String
              # the list of Posts by this author
              posts: [Post] 
            }
            
            type Post {
              id: Int!
              title: String
              votes: Int
              author: Author
            }
            
            # the schema allows the following query
            # to be made
            type Query {
              posts: [Post]
              # author query must receive an id as argument
              author(id: Int!): Author 
            }
            
            input PostUpVote {
                postId: ID
                votes : Int
            }
            
            # this schema allows the following mutation:
            type Mutation {
              upvotePost (
                upvoteArgs : PostUpVote!
              ): Post
            }
            
            # we need to tell the server which types represent the root query
            # and root mutation types. We call them RootQuery and RootMutation by convention.
            schema {
              query: Query
              mutation: Mutation
            }
        """

        def schema = generateSchema(schemaSpec, RuntimeWiring.newRuntimeWiring().build())


        expect:


        schema.getQueryType().name == "Query"
        schema.getMutationType().name == "Mutation"

        //        type Query {
        //            posts: [Post]
        //            author(id: Int!): Author
        //        }

        def postField = schema.getQueryType().getFieldDefinition("posts")
        postField.type instanceof GraphQLList
        unwrap(postField.type).name == "Post"


        def authorField = schema.getQueryType().getFieldDefinition("author")
        authorField.type.name == "Author"
        authorField.description == "author query must receive an id as argument"
        authorField.arguments.get(0).name == "id"
        authorField.arguments.get(0).type instanceof GraphQLNonNull
        unwrap(authorField.arguments.get(0).type).name == "Int"

        //
        // input PostUpVote {
        //        postId: ID
        //        votes : Int
        // }

        // type Mutation {
        //        upvotePost (
        //                upvoteArgs : PostUpVote!
        //        ) : Post
        // }

        def upvotePostField = schema.getMutationType().getFieldDefinition("upvotePost")
        def upvotePostFieldArg = upvotePostField.arguments.get(0)
        upvotePostFieldArg.name == "upvoteArgs"

        upvotePostFieldArg.type instanceof GraphQLNonNull
        unwrap(upvotePostFieldArg.type).name == "PostUpVote"

        (unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType).getField("postId").type.name == "ID"
        (unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType).getField("votes").type.name == "Int"

        def queryType = schema.getQueryType()
        queryType.description == "the schema allows the following query\nto be made"


    }


    def "enum types are handled"() {

        def spec = """     

            enum RGB {
                RED
                GREEN
                BLUE
            }
            
            type Query {
              rgb : RGB
            }
            
            schema {
              query: Query
            }

        """

        def schema = generateSchema(spec, RuntimeWiring.newRuntimeWiring().build())

        expect:

        def rgbField = schema.getQueryType().getFieldDefinition("rgb")
        rgbField.type instanceof GraphQLEnumType
        (rgbField.type as GraphQLEnumType).values.get(0).getValue() == "RED"
        (rgbField.type as GraphQLEnumType).values.get(1).getValue() == "GREEN"
        (rgbField.type as GraphQLEnumType).values.get(2).getValue() == "BLUE"

    }

    def "interface types are handled"() {

        def spec = """     

            interface Foo {
               is_foo : Boolean
            }
            
            interface Goo {
               is_goo : Boolean
            }
                 
            type Query implements Foo {
                is_foo : Boolean
                is_bar : Boolean
            }     
            
            schema {
              query: Query
            }

        """

        def resolver = new TypeResolver() {
            @Override
            GraphQLObjectType getType(Object object) {
                throw new UnsupportedOperationException("Not implemented")
            }
        }
        def wiring = RuntimeWiring.newRuntimeWiring()
                .type({ type -> type.typeName("Foo").typeResolver(resolver) })
                .type({ type -> type.typeName("Goo").typeResolver(resolver) })
                .build()

        def schema = generateSchema(spec, wiring)

        expect:

        schema.queryType.interfaces[0].name == "Foo"
        schema.queryType.fieldDefinitions[0].name == "is_foo"
        schema.queryType.fieldDefinitions[0].type.name == "Boolean"
        schema.queryType.fieldDefinitions[1].name == "is_bar"
        schema.queryType.fieldDefinitions[1].type.name == "Boolean"

    }

}
