package graphql.schema.idl

import graphql.AssertException
import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.schema.GraphQLDirective
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLUnionType
import graphql.schema.PropertyDataFetcher
import graphql.schema.idl.errors.NotAnInputTypeError
import graphql.schema.idl.errors.NotAnOutputTypeError
import graphql.schema.visibility.GraphqlFieldVisibility
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.TestUtil.schema
import static graphql.schema.GraphQLList.list

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
        assert postType.getDefinition().getName() == "Post"
        //
        // make sure that wrapped non null fields stay that way. we had a bug where decorated types lost their decoration
        assert postType.getFieldDefinition("author").type instanceof GraphQLNonNull
        assert (postType.getFieldDefinition("author").type as GraphQLNonNull).wrappedType.name == "Author"

        assert postType.getFieldDefinition("author").getDefinition().getName() == "author"

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

        def inputObjectType = unwrap(upvotePostFieldArg.type) as GraphQLInputObjectType
        assert inputObjectType.getDefinition().getName() == "PostUpVote"

        assert inputObjectType.getField("postId").type.name == "ID"
        assert inputObjectType.getField("votes").type.name == "Int"
        assert inputObjectType.getField("votes").getDefinition().name == "votes"

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

        def schema = schema(schemaSpec)


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

        def schema = schema(spec)


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

        def schema = schema(spec)


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

        def schema = schema(spec)

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

        def schema = schema(spec)

        expect:

        def foobar = schema.getQueryType().getFieldDefinition("foobar")
        foobar.type instanceof GraphQLUnionType
        def unionType = foobar.type as GraphQLUnionType
        unionType.getName() == "FooOrBar"
        unionType.getDefinition().getName() == "FooOrBar"
        def types = unionType.getTypes()
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

        def schema = schema(spec)

        expect:

        def rgbField = schema.getQueryType().getFieldDefinition("rgb")
        rgbField.type instanceof GraphQLEnumType

        def enumType = rgbField.type as GraphQLEnumType
        enumType.getName() == "RGB"
        enumType.getDefinition().getName() == "RGB"

        enumType.values.get(0).getValue() == "RED"
        enumType.values.get(1).getValue() == "GREEN"
        enumType.values.get(2).getValue() == "BLUE"

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

        def schema = schema(spec)

        expect:

        def interfaceType = schema.queryType.interfaces[0] as GraphQLInterfaceType
        interfaceType.name == "Foo"
        interfaceType.getDefinition().getName() == "Foo"

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

        def schema = schema(spec)

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

        def schema = schema(spec)

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
        schema(spec)

        then:
        def err = thrown(NotAnInputTypeError.class)
        err.message == "The type 'CharacterInput' [@11:13] is not an input type, but was used as an input type [@7:42]"
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
        schema(spec)

        then:
        def err = thrown(NotAnOutputTypeError.class)
        err.message == "The type 'CharacterInput' [@11:13] is not an output type, but was used to declare the output type of a field [@7:32]"
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
        def schema = schema(spec)

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
        def schema = schema(spec)

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

    def "doc string comments are used as descriptions by preference"() {
        given:
        def spec = '''
        
        "docstring 1"
        # description 1
        # description 2
        type Query {
            # description 3
            """docstring 3"""
            foo: String
            union: Union
            interface(input: Input): Interface
            enum: Enum
        }

        """docstring 4"""
        # description 4
        union Union = Query
        
        """docstring 5"""
        # description 5
        interface Interface {
            """docstring interface field"""
            # interface field
            foo: String
        }
        
        """docstring 6"""
        # description 6 
        input Input {
            """docstring input field"""
            # input field
            foo: String
        }
        """docstring 7"""
        # description 7
        enum Enum {
            "docstring enum value"
            # enum value
            FOO
        }
        schema {
          query: Query
        }
        '''
        when:
        def schema = schema(spec)

        then:
        schema.getQueryType().description == "docstring 1"
        schema.getQueryType().getFieldDefinition("foo").description == "docstring 3"
        ((GraphQLUnionType) schema.getType("Union")).description == "docstring 4"

        ((GraphQLInterfaceType) schema.getType("Interface")).description == "docstring 5"
        ((GraphQLInterfaceType) schema.getType("Interface")).getFieldDefinition("foo").description == "docstring interface field"

        ((GraphQLInputObjectType) schema.getType("Input")).description == "docstring 6"
        ((GraphQLInputObjectType) schema.getType("Input")).getFieldDefinition("foo").description == "docstring input field"

        ((GraphQLEnumType) schema.getType("Enum")).description == "docstring 7"
        ((GraphQLEnumType) schema.getType("Enum")).getValue("FOO").description == "docstring enum value"
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
        def schema = schema(spec)

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
        def schema = schema(spec, wiring)
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
        def schema = schema(spec)

        GraphQLEnumType enumType = schema.getType("Enum") as GraphQLEnumType

        then:
        enumType.getValue("A").value == "A"
        enumType.getValue("B").value == "B"
        enumType.getValue("C").value == "C"

    }

    @Unroll
    def "when using implicit directive (w/o definition), #argumentName is supported"() {
        setup:
        def spec = """
            type Query @myDirective($argumentName: $argumentValue) {
                foo: String 
            }
        """
        when:
        def wiring = RuntimeWiring.newRuntimeWiring()
                .build()

        def schema = schema(spec, wiring)
        def queryType = schema.queryType

        then:
        def directive = queryType.getDirective("myDirective")
        directive.getArgument(argumentName).type == expectedArgumentType

        where:
        argumentName        | argumentValue     || expectedArgumentType
        "stringArg"         | '"a string"'      || GraphQLString
        "boolArg"           | "true"            || GraphQLBoolean
        "floatArg"          | "4.5"             || GraphQLFloat
        "intArg"            | "5"               || GraphQLInt
        "nullArg"           | "null"            || GraphQLString
        "emptyArrayArg"     | "[]"              || list(GraphQLString)
        "arrayNullsArg"     | "[null, null]"    || list(GraphQLString)
        "arrayArg"          | "[3,4,6]"         || list(GraphQLInt)
        "arrayWithNullsArg" | "[null,3,null,6]" || list(GraphQLInt)
    }

    @Unroll
    def "when using implicit directive (w/o definition), #argumentName is NOT supported"() {
        setup:
        def spec = """
            type Query @myDirective($argumentName: $argumentValue) {
                foo: String 
            }
        """
        when:
        def wiring = RuntimeWiring.newRuntimeWiring()
                .build()
        schema(spec, wiring)

        then:
        def ex = thrown(AssertException)
        ex.message == expectedErrorMessage

        where:
        argumentName          | argumentValue               || expectedErrorMessage
        "objArg"              | '{ hi: "John"}'             || "Internal error: should never happen: Directive values of type 'ObjectValue' are not supported yet"
        "enumArg"             | "MONDAY"                    || "Internal error: should never happen: Directive values of type 'EnumValue' are not supported yet"
        "polymorphicArrayArg" | '["one", { hi: "John"}, 5]' || "Arrays containing multiple types of values are not supported yet. Detected the following types [IntValue,ObjectValue,StringValue]"
    }

    def "deprecated directive is supported"() {
        given:
        def spec = """
        type Query {
            foo: Enum @deprecated(reason : "foo reason")
            bar: String @deprecated
            baz: String
        }
        enum Enum {
            foo @deprecated(reason : "foo reason")
            bar @deprecated
            baz 
        }
        schema {
            query: Query
        }
        """
        when:
        def wiring = RuntimeWiring.newRuntimeWiring()
                .build()

        def schema = schema(spec, wiring)
        GraphQLEnumType enumType = schema.getType("Enum") as GraphQLEnumType
        GraphQLObjectType queryType = schema.getType("Query") as GraphQLObjectType

        then:
        enumType.getValue("foo").getDeprecationReason() == "foo reason"
        enumType.getValue("bar").getDeprecationReason() == "No longer supported" // default according to spec
        !enumType.getValue("baz").isDeprecated()

        queryType.getFieldDefinition("foo").getDeprecationReason() == "foo reason"
        queryType.getFieldDefinition("bar").getDeprecationReason() == "No longer supported" // default according to spec
        !queryType.getFieldDefinition("baz").isDeprecated()
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

        def schema = schema(spec)

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

        def schema = schema(spec)

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

        def schema = schema(spec)

        expect:

        schema.getType("ReferencedA") instanceof GraphQLObjectType
        schema.getType("ReferencedB") instanceof GraphQLObjectType
        schema.getType("UnReferencedA") instanceof GraphQLObjectType
        schema.getType("UnReferencedB") instanceof GraphQLInputObjectType
        schema.getType("UnReferencedC") instanceof GraphQLInterfaceType
        schema.getType("UnReferencedD") instanceof GraphQLUnionType
    }

    def "scalar default value is parsed"() {
        def spec = """
            type Query {
              field(arg1 : Int! = 10, arg2 : [Int!]! = [20]) : String
            }
        """

        def schema = schema(spec)
        schema.getType("Query") instanceof GraphQLObjectType
        GraphQLObjectType query = schema.getType("Query") as GraphQLObjectType
        Object arg1 = query.getFieldDefinition("field").getArgument("arg1").defaultValue
        Object arg2 = query.getFieldDefinition("field").getArgument("arg2").defaultValue

        expect:
        arg1 instanceof Integer
        arg2 instanceof List
        (arg2 as List).get(0) instanceof Integer
    }

    def "null default arguments are ok"() {
        def spec = """
            type Query {
              field(argNoDefault : Int) : String
            }
        """

        def schema = schema(spec)
        schema.getType("Query") instanceof GraphQLObjectType
        GraphQLObjectType query = schema.getType("Query") as GraphQLObjectType
        Object argNoDefault = query.getFieldDefinition("field").getArgument("argNoDefault").defaultValue

        expect:
        argNoDefault == null
    }

    def "object type directives are gathered and turned into runtime objects with arguments"() {
        def spec = """
            type Query @directive1 {
              field1 : String @fieldDirective1
            }
            
            extend type Query @directive2 {
                field2 : String @fieldDirective2
            }

            extend type Query @directive2
            
            extend type Query @directive3
            
            extend type Query @directiveWithArgs(strArg : "String", intArg : 1, boolArg : true, floatArg : 1.1, nullArg : null)
            
                
        """

        def schema = schema(spec)
        GraphQLObjectType type = schema.getType("Query") as GraphQLObjectType

        expect:
        type.getDirectives().size() == 4
        type.getDirectives()[0].name == "directive1"
        type.getDirectives()[1].name == "directive2"
        type.getDirectives()[2].name == "directive3"

        // test that fields can have directives as well

        def field1 = type.getFieldDefinition("field1")
        field1.getDirectives().size() == 1
        def fieldDirective1 = field1.getDirectives()[0]
        fieldDirective1.getName() == "fieldDirective1"

        def field2 = type.getFieldDefinition("field2")
        field2.getDirectives().size() == 1
        def fieldDirective2 = field2.getDirectives()[0]
        fieldDirective2.getName() == "fieldDirective2"


        def directive = type.getDirectives()[3] as GraphQLDirective
        directive.name == "directiveWithArgs"
        directive.arguments.size() == 5

        directive.arguments[argIndex].name == argName
        directive.arguments[argIndex].type == argType
        directive.arguments[argIndex].value == argValue

        where:
        argIndex | argName    | argType        | argValue
        0        | "strArg"   | GraphQLString  | "String"
        1        | "intArg"   | GraphQLInt     | 1
        2        | "boolArg"  | GraphQLBoolean | true
        3        | "floatArg" | GraphQLFloat   | 1.1
        4        | "nullArg"  | GraphQLString  | null

    }

    def "other type directives are captured"() {
        def spec = """
            type Query {
              field1 : String
            }
            type A  {
                fieldA : String
            }
            type B  {
                fieldB : String
            }
            
            interface IFace @IFaceDirective {
                field1 : String
            }
            
            union Onion @OnionDirective = A | B
            
            enum Numb @NumbDirective {
                X @EnumValueDirective,
                Y
            }
            
            input Puter @PuterDirective {
                inputField : String @InputFieldDirective
            }
        """

        def schema = schema(spec)
        GraphQLDirectiveContainer container = schema.getType(typeName) as GraphQLDirectiveContainer

        expect:

        container.getDirective(directiveName) != null

        if (container instanceof GraphQLEnumType) {
            def evd = ((GraphQLEnumType) container).getValue("X").getDirective("EnumValueDirective")
            assert evd != null
        }
        if (container instanceof GraphQLInputObjectType) {
            def ifd = ((GraphQLInputObjectType) container).getField("inputField").getDirective("InputFieldDirective")
            assert ifd != null
        }

        where:
        typeName | directiveName
        "IFace"  | "IFaceDirective"
        "Onion"  | "OnionDirective"
        "Numb"   | "NumbDirective"
        "Puter"  | "PuterDirective"
    }

    def "input object default value is parsed"() {
        def spec = """
            input InputObject {
                str : String
                num : Int
            }
            type Query {
              field(arg : InputObject = {str : "string", num : 100}) : String
            }
        """

        def schema = schema(spec)
        schema.getType("Query") instanceof GraphQLObjectType
        GraphQLObjectType query = schema.getType("Query") as GraphQLObjectType
        Object arg = query.getFieldDefinition("field").getArgument("arg").defaultValue as Map

        expect:
        arg["str"] instanceof String
        arg["num"] instanceof Integer
    }

    def "field visibility is used"() {
        def spec = """
            type Query {
              field : String
            }
        """

        GraphqlFieldVisibility fieldVisibility = new GraphqlFieldVisibility() {
            @Override
            List<GraphQLFieldDefinition> getFieldDefinitions(GraphQLFieldsContainer fieldsContainer) {
                return null
            }

            @Override
            GraphQLFieldDefinition getFieldDefinition(GraphQLFieldsContainer fieldsContainer, String fieldName) {
                return null
            }
        }

        def schema = schema(spec, RuntimeWiring.newRuntimeWiring().fieldVisibility(fieldVisibility).build())

        expect:

        schema.getFieldVisibility() == fieldVisibility

    }

    def "empty types are allowed and expanded"() {
        def spec = """
            type Query
            
            interface IAge {
                age : Int
            }
            
            extend type Query {
                name : String
            }

            extend type Query implements IAge {
                age : Int
            }
            
        """

        def schema = schema(spec)
        schema.getType("Query") instanceof GraphQLObjectType
        GraphQLObjectType query = schema.getType("Query") as GraphQLObjectType

        expect:
        query.getFieldDefinitions().size() == 2
        query.getInterfaces().size() == 1
        query.getInterfaces().get(0).getName() == 'IAge'
    }

    def "object extension types are combined"() {

    }

    def "interface extension types are combined"() {
        def spec = """
            type Query implements IAgeAndHeight {
                age : Int
                height : Int
            }
            
            
            interface IAgeAndHeight @directive {
                age : Int
            }

            extend interface IAgeAndHeight {
                height : Int @directiveField
            }
            
        """

        def schema = schema(spec)
        GraphQLObjectType query = schema.getType("Query") as GraphQLObjectType

        expect:
        query.getFieldDefinitions().size() == 2
        query.getInterfaces().size() == 1
        query.getInterfaces().get(0).getName() == 'IAgeAndHeight'
        (query.getInterfaces().get(0) as GraphQLInterfaceType).getDirectivesByName().containsKey("directive")
        (query.getInterfaces().get(0) as GraphQLInterfaceType).getFieldDefinition("height").getDirectivesByName().containsKey("directiveField")
    }

    def "union extension types are combined"() {
        def spec = """
            type Query {
                field :String
            }
            
            
            type Foo {
                field :String
            }

            type Bar {
                field :String
            }

            type Baz {
                field :String
            }
                

            union FooBar = Foo
            
            extend union FooBar = Bar | Baz
            
            extend union FooBar @directive
            
        """

        def schema = schema(spec)
        GraphQLUnionType unionType = schema.getType("FooBar") as GraphQLUnionType

        expect:
        unionType.types.size() == 3
        unionType.types.stream().anyMatch({ t -> (t.getName() == "Foo") })
        unionType.types.stream().anyMatch({ t -> (t.getName() == "Bar") })
        unionType.types.stream().anyMatch({ t -> (t.getName() == "Baz") })
        unionType.directivesByName.containsKey("directive")
    }

    def "enum extension types are combined"() {
        def spec = """
            type Query {
                field :String
            }
            
            
            enum Numb {
                A,B
            }
            
            extend enum Numb {
                C
            }

            extend enum Numb @directive{
                D
            }
        """

        def schema = schema(spec)
        GraphQLEnumType enumType = schema.getType("Numb") as GraphQLEnumType

        expect:
        enumType.values.size() == 4
        enumType.values.stream().anyMatch({ t -> (t.getName() == "A") })
        enumType.values.stream().anyMatch({ t -> (t.getName() == "B") })
        enumType.values.stream().anyMatch({ t -> (t.getName() == "C") })
        enumType.values.stream().anyMatch({ t -> (t.getName() == "D") })
        enumType.directivesByName.containsKey("directive")
    }

    def "input extension types are combined"() {
        def spec = """
            type Query {
                field :String
            }
            
            
            input Puter {
                fieldA : String
            }

            extend input Puter {
                fieldB : String
            }

            extend input Puter {
                fieldC : String
            }

            extend input Puter @directive {
                fieldD : String
            }
            
        """

        def schema = schema(spec)
        GraphQLInputObjectType inputObjectType = schema.getType("Puter") as GraphQLInputObjectType

        expect:
        inputObjectType.fields.size() == 4
        inputObjectType.fields.stream().anyMatch({ t -> (t.getName() == "fieldA") })
        inputObjectType.fields.stream().anyMatch({ t -> (t.getName() == "fieldB") })
        inputObjectType.fields.stream().anyMatch({ t -> (t.getName() == "fieldC") })
        inputObjectType.fields.stream().anyMatch({ t -> (t.getName() == "fieldD") })
        inputObjectType.directivesByName.containsKey("directive")
    }

    def "arguments can have directives (which themselves can have arguments)"() {
        def spec = """
            type Query {
                obj : Object
            }
            
            type Object {
                field(argStr : String @strDirective @secondDirective, argInt : Int @intDirective(inception : true) @thirdDirective ) : String
            }
        """

        def schema = schema(spec)
        GraphQLObjectType type = schema.getType("Object") as GraphQLObjectType
        def fieldDefinition = type.getFieldDefinition("field")
        def argStr = fieldDefinition.getArgument("argStr")
        def argInt = fieldDefinition.getArgument("argInt")

        expect:
        argStr.getDirectives().size() == 2
        argStr.getDirective("strDirective") != null
        argStr.getDirective("secondDirective") != null

        argInt.getDirectives().size() == 2

        argInt.getDirective("thirdDirective") != null

        def intDirective = argInt.getDirective("intDirective")
        intDirective.name == "intDirective"
        intDirective.arguments.size() == 1
        def directiveArg = intDirective.getArgument("inception")
        directiveArg.name == "inception"
        directiveArg.type == GraphQLBoolean
        directiveArg.value == true
        directiveArg.defaultValue == null
    }

    def "directives definitions can be made"() {
        def spec = """
            directive @testDirective(knownArg : String = "defaultValue") on SCHEMA | SCALAR | 
                            OBJECT | FIELD_DEFINITION |
                            ARGUMENT_DEFINITION | INTERFACE | UNION | 
                            ENUM | ENUM_VALUE | 
                            INPUT_OBJECT | INPUT_FIELD_DEFINITION

            type Query {
                f : String @testDirective
            }
        """

        when:
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(true)

        then:
        options.isEnforceSchemaDirectives()

        when:
        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def directive = schema.getDirective("testDirective")
        directive.name == "testDirective"
        directive.validLocations() == EnumSet.of(
                Introspection.DirectiveLocation.SCHEMA,
                Introspection.DirectiveLocation.SCALAR,
                Introspection.DirectiveLocation.OBJECT,
                Introspection.DirectiveLocation.FIELD_DEFINITION,
                Introspection.DirectiveLocation.ARGUMENT_DEFINITION,
                Introspection.DirectiveLocation.INTERFACE,
                Introspection.DirectiveLocation.UNION,
                Introspection.DirectiveLocation.ENUM,
                Introspection.DirectiveLocation.ENUM_VALUE,
                Introspection.DirectiveLocation.INPUT_OBJECT,
                Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION,
        )
        directive.getArgument("knownArg").type == GraphQLString
        directive.getArgument("knownArg").defaultValue == "defaultValue"
    }

    def "directive definitions don't have to provide default values"() {
        def spec = """
            directive @test1(include: Boolean!) on FIELD_DEFINITION
            
            directive @test2(include: Boolean!  = true) on FIELD_DEFINITION
            
            type Query {
                f1 : String @test1(include : false)
                f2 : String @test2
            }
        """

        when:
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(true)

        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def directiveTest1 = schema.getDirective("test1")
        directiveTest1.getArgument("include").type == GraphQLNonNull.nonNull(GraphQLBoolean)
        directiveTest1.getArgument("include").value == null

        def directiveTest2 = schema.getDirective("test2")
        directiveTest2.getArgument("include").type == GraphQLNonNull.nonNull(GraphQLBoolean)
        directiveTest2.getArgument("include").value == true
        directiveTest2.getArgument("include").defaultValue == true

    }

    def "missing directive arguments are transferred as are default values"() {
        def spec = """
            directive @testDirective(
                knownArg1 : String = "defaultValue1", 
                knownArg2 : Int = 666, 
                knownArg3 : String, 
                ) 
                on FIELD_DEFINITION

            type Query {
                f : String @testDirective(knownArg1 : "overrideVal1")
            }
        """

        when:
        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(true)

        then:
        options.isEnforceSchemaDirectives()

        when:
        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def directive = schema.getObjectType("Query").getFieldDefinition("f").getDirective("testDirective")
        directive.getArgument("knownArg1").type == GraphQLString
        directive.getArgument("knownArg1").value == "overrideVal1"
        directive.getArgument("knownArg1").defaultValue == "defaultValue1"

        directive.getArgument("knownArg2").type == GraphQLInt
        directive.getArgument("knownArg2").value == 666
        directive.getArgument("knownArg2").defaultValue == 666

        directive.getArgument("knownArg3").type == GraphQLString
        directive.getArgument("knownArg3").value == null
        directive.getArgument("knownArg3").defaultValue == null
    }

    def "deprecated directive is implicit"() {
        def spec = """

            type Query {
                f1 : String @deprecated
                f2 : String @deprecated(reason : "Just because")
            }
        """

        def options = SchemaGenerator.Options.defaultOptions().enforceSchemaDirectives(true)

        when:
        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def f1 = schema.getObjectType("Query").getFieldDefinition("f1")
        f1.getDeprecationReason() == "No longer supported" // spec default text

        def directive = f1.getDirective("deprecated")
        directive.name == "deprecated"
        directive.getArgument("reason").type == GraphQLString
        directive.getArgument("reason").value == "No longer supported"
        directive.getArgument("reason").defaultValue == "No longer supported"
        directive.validLocations().collect { it.name() } == [Introspection.DirectiveLocation.FIELD_DEFINITION.name()]

        when:
        def f2 = schema.getObjectType("Query").getFieldDefinition("f2")

        then:
        f2.getDeprecationReason() == "Just because"

        def directive2 = f2.getDirective("deprecated")
        directive2.name == "deprecated"
        directive2.getArgument("reason").type == GraphQLString
        directive2.getArgument("reason").value == "Just because"
        directive2.getArgument("reason").defaultValue == "No longer supported"
        directive2.validLocations().collect { it.name() } == [Introspection.DirectiveLocation.FIELD_DEFINITION.name()]

    }

    def "@fetch directive is respected"() {
        def spec = """             

            directive @fetch(from : String!) on FIELD_DEFINITION

            type Query {
                name : String,
                homePlanet: String @fetch(from : "planetOfBirth")
            }
        """

        def wiring = RuntimeWiring.newRuntimeWiring().build()
        def schema = schema(spec, wiring)

        GraphQLObjectType type = schema.getType("Query") as GraphQLObjectType

        expect:
        def fetcher = type.getFieldDefinition("homePlanet").getDataFetcher()
        fetcher instanceof PropertyDataFetcher

        PropertyDataFetcher propertyDataFetcher = fetcher as PropertyDataFetcher
        propertyDataFetcher.getPropertyName() == "planetOfBirth"
        //
        // no directive - plain name
        //
        def fetcher2 = type.getFieldDefinition("name").getDataFetcher()
        fetcher2 instanceof PropertyDataFetcher

        PropertyDataFetcher propertyDataFetcher2 = fetcher2 as PropertyDataFetcher
        propertyDataFetcher2.getPropertyName() == "name"

    }

}