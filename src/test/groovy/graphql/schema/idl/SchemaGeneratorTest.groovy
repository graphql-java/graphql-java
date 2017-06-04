package graphql.schema.idl

import graphql.TestUtil
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.idl.errors.NotAnInputTypeError
import graphql.schema.idl.errors.NotAnOutputTypeError
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString

class SchemaGeneratorTest extends Specification {

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

    void commonSchemaAsserts(GraphQLSchema schema) {
        assert schema.getQueryType().name == "Query"
        assert schema.getMutationType().name == "Mutation"

        //        type Query {
        //            posts: [Post]
        //            author(id: Int!): Author
        //        }

        def postField = schema.getQueryType().getFieldDefinition("posts")
        assert postField.type instanceof GraphQLList
        assert unwrap(postField.type).name == "Post"


        def authorField = schema.getQueryType().getFieldDefinition("author")
        assert authorField.type.name == "Author"
        assert authorField.description == " author query must receive an id as argument"
        assert authorField.arguments.get(0).name == "id"
        assert authorField.arguments.get(0).type instanceof GraphQLNonNull
        assert unwrap(authorField.arguments.get(0).type).name == "Int"

        //type Post {
        //    id: Int!
        //            title: String
        //    votes: Int
        //    author: Author!
        //}
        GraphQLObjectType postType = schema.getType("Post") as GraphQLObjectType
        assert postType.name == "Post"
        //
        // make sure that wrapped non null fields stay that way. we had a bug where decorated types lost their decoration
        assert postType.getFieldDefinition("author").type instanceof GraphQLNonNull
        assert (postType.getFieldDefinition("author").type as GraphQLNonNull).wrappedType.name == "Author"

        //type Author {
        //    # the ! means that every author object _must_ have an id
        //    id: Int!
        //            firstName: String
        //    lastName: String
        //    # the list of Posts by this author
        //    posts: [Post]!
        //}
        GraphQLObjectType authorType = schema.getType("Author") as GraphQLObjectType
        assert authorType.name == "Author"
        //
        // make sure that wrapped list fields stay that way. we had a bug where decorated types lost their decoration
        assert authorType.getFieldDefinition("posts").type instanceof GraphQLNonNull
        def wrappedList = (authorType.getFieldDefinition("posts").type as GraphQLNonNull).wrappedType
        assert wrappedList instanceof GraphQLList
        assert (wrappedList as GraphQLList).wrappedType.name == "Post"

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
        assert upvotePostFieldArg.name == "upvoteArgs"

        assert upvotePostFieldArg.type instanceof GraphQLNonNull
        assert unwrap(upvotePostFieldArg.type).name == "PostUpVote"

        assert (unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType).getField("postId").type.name == "ID"
        assert (unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType).getField("votes").type.name == "Int"

        def queryType = schema.getQueryType()
        assert queryType.description == " the schema allows the following query\n to be made"

    }


    def "test simple schema generate"() {

        def schemaSpec = """
            type Author {
                # the ! means that every author object _must_ have an id
              id: Int! 
              firstName: String
              lastName: String
              # the list of Posts by this author
              posts: [Post]! 
            }
            
            type Post {
              id: Int!
              title: String
              votes: Int
              author: Author!
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

        def schema = TestUtil.schema(schemaSpec)


        expect:

        commonSchemaAsserts(schema)
    }


    def "schema can come from multiple sources and be bound together"() {
        def schemaSpec1 = """
            type Author {
                # the ! means that every author object _must_ have an id
              id: Int! 
              firstName: String
              lastName: String
              # the list of Posts by this author
              posts: [Post]! 
            }
            """
        def schemaSpec2 = """
            
            type Post {
              id: Int!
              title: String
              votes: Int
              author: Author!
            }
        """

        def schemaSpec3 = """
            # the schema allows the following query
            # to be made
            type Query {
                posts:
                [Post]
                # author query must receive an id as argument
                author(id: Int !): Author
            }
    
            input PostUpVote {
                postId:
                ID
                votes:
                Int
            }
    
            # this schema allows the following mutation:
                    type Mutation {
                upvotePost(
                        upvoteArgs: PostUpVote !
                ): Post
            }
    
            # we need to tell the server which types represent the root query
            # and root mutation types.We call them RootQuery and RootMutation by convention.
            schema {
                query:
                Query
                mutation:
                Mutation
            }
        """

        def typeRegistry1 = new SchemaParser().parse(schemaSpec1)
        def typeRegistry2 = new SchemaParser().parse(schemaSpec2)
        def typeRegistry3 = new SchemaParser().parse(schemaSpec3)

        typeRegistry1.merge(typeRegistry2).merge(typeRegistry3)

        def schema = new SchemaGenerator().makeExecutableSchema(typeRegistry1, RuntimeWiring.newRuntimeWiring().build())

        expect:

        commonSchemaAsserts(schema)


    }

    def "union type: union member used two times "() {
        def spec = """     
            type Query {
                foobar: FooOrBar
                foo: Foo
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

        def schema = TestUtil.schema(spec)


        expect:

        def foobar = schema.getQueryType().getFieldDefinition("foobar")
        foobar.type instanceof GraphQLUnionType
        def types = ((GraphQLUnionType) foobar.type).getTypes()
        types.size() == 2
        types[0] instanceof GraphQLObjectType
        types[1] instanceof GraphQLObjectType
        types[0].name == "Foo"
        types[1].name == "Bar"

    }

    def "union type: union members only used once"() {
        def spec = """     
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

        def schema = TestUtil.schema(spec)


        expect:

        def foobar = schema.getQueryType().getFieldDefinition("foobar")
        foobar.type instanceof GraphQLUnionType
        def types = ((GraphQLUnionType) foobar.type).getTypes()
        types.size() == 2
        types[0] instanceof GraphQLObjectType
        types[1] instanceof GraphQLObjectType
        types[0].name == "Foo"
        types[1].name == "Bar"

    }

    def "union type: union declared before members"() {
        def spec = """     
            union FooOrBar = Foo | Bar
            
            type Foo {
               name: String 
            }
            
            type Bar {
                other: String
            }
            
            type Query {
                foobar: FooOrBar
            }
            
            schema {
              query: Query
            }
        """

        def schema = TestUtil.schema(spec)

        expect:

        def foobar = schema.getQueryType().getFieldDefinition("foobar")
        foobar.type instanceof GraphQLUnionType
        def types = ((GraphQLUnionType) foobar.type).getTypes()
        types.size() == 2
        types[0] instanceof GraphQLObjectType
        types[1] instanceof GraphQLObjectType
        types[0].name == "Foo"
        types[1].name == "Bar"

    }

    def "union type: recursive definition via union type: Foo -> FooOrBar -> Foo  "() {
        def spec = """     

            schema {
              query: Foo
            }
            
            type Foo {
                foobar: FooOrBar
            }
            
            union FooOrBar = Foo | Bar
            
            type Bar {
                other: String
            }
            
            

        """

        def schema = TestUtil.schema(spec)

        expect:

        def foobar = schema.getQueryType().getFieldDefinition("foobar")
        foobar.type instanceof GraphQLUnionType
        def types = ((GraphQLUnionType) foobar.type).getTypes()
        types.size() == 2
        types[0] instanceof GraphQLObjectType
        types[1] instanceof GraphQLObjectType
        types[0].name == "Foo"
        types[1].name == "Bar"

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

        def schema = TestUtil.schema(spec)

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

        def schema = TestUtil.schema(spec)

        expect:

        schema.queryType.interfaces[0].name == "Foo"
        schema.queryType.fieldDefinitions[0].name == "is_foo"
        schema.queryType.fieldDefinitions[0].type.name == "Boolean"
        schema.queryType.fieldDefinitions[1].name == "is_bar"
        schema.queryType.fieldDefinitions[1].type.name == "Boolean"

    }

    def "type extensions can be specified multiple times #406"() {

        def spec = """
            interface Interface1 {
               extraField1 : String
            }     
            interface Interface2 {
               extraField1 : String
               extraField2 : Int
            }     
            interface Interface3 {
               extraField1 : String
               extraField3 : ID
            }     
            type BaseType {
               baseField : String
            }
            
            extend type BaseType implements Interface1 {
               extraField1 : String
            }
            extend type BaseType implements Interface2 {
               extraField1 : String
               extraField2 : Int
            }
            extend type BaseType implements Interface3 {
               extraField1 : String
               extraField3 : ID
            }
            extend type BaseType {
               extraField4 : Boolean
            }
            extend type BaseType {
               extraField5 : Boolean!
            }
            #
            # if we repeat a definition, that's ok as long as its the same types as before
            # they will be de-duped since the effect is the same
            #
            extend type BaseType implements Interface1 {
               extraField1 : String
            }
            
            schema {
              query: BaseType
            }
        """

        def schema = TestUtil.schema(spec)

        expect:

        GraphQLObjectType type = schema.getType("BaseType") as GraphQLObjectType

        type.fieldDefinitions.size() == 6

        type.fieldDefinitions[0].name == "baseField"
        type.fieldDefinitions[0].type.name == "String"

        type.fieldDefinitions[1].name == "extraField1"
        type.fieldDefinitions[1].type.name == "String"

        type.fieldDefinitions[2].name == "extraField2"
        type.fieldDefinitions[2].type.name == "Int"

        type.fieldDefinitions[3].name == "extraField3"
        type.fieldDefinitions[3].type.name == "ID"

        type.fieldDefinitions[4].name == "extraField4"
        type.fieldDefinitions[4].type.name == "Boolean"

        type.fieldDefinitions[5].name == "extraField5"
        type.fieldDefinitions[5].type instanceof GraphQLNonNull

        def interfaces = type.getInterfaces()

        interfaces.size() == 3
        interfaces[0].name == "Interface1"
        interfaces[1].name == "Interface2"
        interfaces[2].name == "Interface3"

    }

    def "read me type example makes sense"() {

        def spec = """             
            schema {
              query: Human
            }
            type Episode {
                name : String
            }
            
            interface Character {
                name: String!
            }
                
            type Human {
                id: ID!
                name: String!
            }
            extend type Human implements Character {
                name: String!
                friends: [Character]
            }
            extend type Human {
                appearsIn: [Episode]!
                homePlanet: String
            }
        """

        def schema = TestUtil.schema(spec)

        expect:

        GraphQLObjectType type = schema.getQueryType()

        type.name == "Human"
        type.fieldDefinitions[0].name == "id"
        type.fieldDefinitions[1].name == "name"
        type.fieldDefinitions[2].name == "friends"
        type.fieldDefinitions[3].name == "appearsIn"
        type.fieldDefinitions[4].name == "homePlanet"

        type.interfaces.size() == 1
        type.interfaces[0].name == "Character"
    }

    def "Type used as inputType should throw appropriate error #425"() {
        when:
        def spec = """
            schema {
                query: Query
            }
            
            type Query {
                findCharacter(character: CharacterInput!): Boolean
            }
            
            # CharacterInput must be an input, but is a type
            type CharacterInput {
                firstName: String
                lastName: String
                family: Boolean
            }
        """
        TestUtil.schema(spec)

        then:
        def err = thrown(NotAnInputTypeError.class)
        err.message == "expected InputType, but found CharacterInput type [@11:13]"
    }

    def "InputType used as type should throw appropriate error #425"() {
        when:
        def spec = """
            schema {
                query: Query
            }
            
            type Query {
                findCharacter: CharacterInput
            }
            
            # CharacterInput must be an input, but is a type
            input CharacterInput {
                firstName: String
                lastName: String
                family: Boolean
            }
        """
        TestUtil.schema(spec)

        then:
        def err = thrown(NotAnOutputTypeError.class)
        err.message == "expected OutputType, but found CharacterInput type [@11:13]"
    }

    def "schema with subscription"() {
        given:
        def spec = """
            schema {
                query: Query
                subscription: Subscription
            }
            type Query {
                foo: String
            }
            
            type Subscription {
                foo: String 
            }
            """
        when:
        def schema = TestUtil.schema(spec)

        then:
        schema.getSubscriptionType().name == "Subscription"
    }


    def "comments are used as descriptions"() {
        given:
        def spec = """
        #description 1
        # description 2
        type Query {
            # description 3
            foo: String
            union: Union
            interface(input: Input): Interface
            enum: Enum
        }
        # description 4
        union Union = Query
        
        # description 5
        interface Interface {
            # interface field
            foo: String
        }
        # description 6 
        input Input {
            # input field
            foo: String
        }
        # description 7
        enum Enum {
            # enum value
            FOO
        }
        schema {
          query: Query
        }
        """
        when:
        def schema = TestUtil.schema(spec)

        then:
        schema.getQueryType().description == "description 1\n description 2"
        schema.getQueryType().getFieldDefinition("foo").description == " description 3"
        ((GraphQLUnionType) schema.getType("Union")).description == " description 4"

        ((GraphQLInterfaceType) schema.getType("Interface")).description == " description 5"
        ((GraphQLInterfaceType) schema.getType("Interface")).getFieldDefinition("foo").description == " interface field"

        ((GraphQLInputObjectType) schema.getType("Input")).description == " description 6 "
        ((GraphQLInputObjectType) schema.getType("Input")).getFieldDefinition("foo").description == " input field"

        ((GraphQLEnumType) schema.getType("Enum")).description == " description 7"
        ((GraphQLEnumType) schema.getType("Enum")).getValue("FOO").description == " enum value"
    }

    def "comments are separated from descriptions with empty lines"() {
        given:
        def spec = """
        # should be ignored comment
        #
        # description 1
        # description 2
        type Query {
            # this should be ignored
            # and this
            #
            # and this after an empty line
            # and the last one that should be ignored
            # 
            # description 3
            # description 4
            foo: String
            # ignored and with not description following
            #
            bar: String
        }
        schema {
          query: Query
        }
        """
        when:
        def schema = TestUtil.schema(spec)

        then:
        schema.getQueryType().description == " description 1\n description 2"
        schema.getQueryType().getFieldDefinition("foo").description == " description 3\n description 4"
    }

    enum ExampleEnum {
        A,
        B,
        C
    }

    def "static enum values provider"() {
        given:
        def spec = """
        type Query {
            foo: Enum
        }
        enum Enum {
            A
            B
            C 
        }
        schema {
            query: Query
        }
        """
        def enumValuesProvider = new NaturalEnumValuesProvider<ExampleEnum>(ExampleEnum.class)
        when:

        def wiring = RuntimeWiring.newRuntimeWiring()
                .type("Enum", { TypeRuntimeWiring.Builder it -> it.enumValues(enumValuesProvider) } as UnaryOperator)
                .build()
        def schema = TestUtil.schema(spec, wiring)
        GraphQLEnumType enumType = schema.getType("Enum") as GraphQLEnumType

        then:
        enumType.getValue("A").value == ExampleEnum.A
        enumType.getValue("B").value == ExampleEnum.B
        enumType.getValue("C").value == ExampleEnum.C
    }

    def "enum with no values provider: value is the name"() {
        given:
        def spec = """
        type Query {
            foo: Enum
        }
        enum Enum {
            A
            B
            C 
        }
        schema {
            query: Query
        }
        """
        when:
        def schema = TestUtil.schema(spec)

        GraphQLEnumType enumType = schema.getType("Enum") as GraphQLEnumType

        then:
        enumType.getValue("A").value == "A"
        enumType.getValue("B").value == "B"
        enumType.getValue("C").value == "C"

    }


    def "schema is optional if there is a type called Query"() {

        def spec = """     
            type Query {
              field : String
            }
            
            type mutation {   # case matters this is not an implicit mutation
              field : Int
            }

            type subscription { # case matters this is not an implicit subscription
              field : Boolean
            }
        """

        def schema = TestUtil.schema(spec)

        expect:

        schema != null
        schema.getQueryType() != null
        schema.getMutationType() == null
        schema.getSubscriptionType() == null
        schema.getQueryType().getFieldDefinition("field").getType() == GraphQLString

    }

    def "schema is optional if there is a type called Query while Mutation and Subscription will be found"() {

        def spec = """     
            type Query {
              field : String
            }

            type Mutation {
              field : Int
            }

            type Subscription {
              field : Boolean
            }
        """

        def schema = TestUtil.schema(spec)

        expect:

        schema != null
        schema.getQueryType() != null
        schema.getMutationType() != null
        schema.getSubscriptionType() != null
        schema.getQueryType().getFieldDefinition("field").getType() == GraphQLString
        schema.getMutationType().getFieldDefinition("field").getType() == GraphQLInt
        schema.getSubscriptionType().getFieldDefinition("field").getType() == GraphQLBoolean

    }

    def "builds additional types not referenced from schema top level"() {
        def spec = """      
            schema {
                query : Query
            }
            
            type Query {
              fieldA : ReferencedA
              fieldB : ReferencedB
            }
            
            type ReferencedA {
              field : String
            }

            type ReferencedB {
              field : String
            }

            type UnReferencedA {
              field : String
            }
            
            input UnReferencedB {
              field : String
            }
            
            interface UnReferencedC {
                field : String
            }
            
            union UnReferencedD = ReferencedA | ReferencedB  
        """

        def schema = TestUtil.schema(spec)

        expect:

        schema.getType("ReferencedA") instanceof GraphQLObjectType
        schema.getType("ReferencedB") instanceof GraphQLObjectType
        schema.getType("UnReferencedA") instanceof GraphQLObjectType
        schema.getType("UnReferencedB") instanceof GraphQLInputObjectType
        schema.getType("UnReferencedC") instanceof GraphQLInterfaceType
        schema.getType("UnReferencedD") instanceof GraphQLUnionType
    }
}