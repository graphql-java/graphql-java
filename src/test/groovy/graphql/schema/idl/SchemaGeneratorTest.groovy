package graphql.schema.idl

import graphql.TestUtil
import graphql.introspection.Introspection
import graphql.language.Node
import graphql.schema.DataFetcher
import graphql.schema.DataFetcherFactory
import graphql.schema.DataFetcherFactoryEnvironment
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLAppliedDirective
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLDirectiveContainer
import graphql.schema.GraphQLEnumType
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLFieldsContainer
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNamedType
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLScalarType
import graphql.schema.GraphQLSchema
import graphql.schema.GraphQLType
import graphql.schema.GraphQLTypeUtil
import graphql.schema.GraphQLUnionType
import graphql.schema.GraphqlTypeComparatorRegistry
import graphql.schema.idl.errors.NotAnInputTypeError
import graphql.schema.idl.errors.NotAnOutputTypeError
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.visibility.GraphqlFieldVisibility
import spock.lang.Specification

import java.util.function.UnaryOperator

import static graphql.Scalars.GraphQLBoolean
import static graphql.Scalars.GraphQLFloat
import static graphql.Scalars.GraphQLInt
import static graphql.Scalars.GraphQLString
import static graphql.language.AstPrinter.printAst
import static graphql.schema.GraphQLCodeRegistry.newCodeRegistry
import static graphql.schema.idl.SchemaGenerator.Options.defaultOptions
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class SchemaGeneratorTest extends Specification {

    static def newRuntimeWiring() {
        return RuntimeWiring.newRuntimeWiring()
                .comparatorRegistry(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY)
    }

    static GraphQLSchema schema(String sdl) {
        def runtimeWiringAsIs = newRuntimeWiring()
                .comparatorRegistry(GraphqlTypeComparatorRegistry.BY_NAME_REGISTRY)
                .wiringFactory(TestUtil.mockWiringFactory)
                .build()
        return schema(sdl, runtimeWiringAsIs)
    }

    static GraphQLSchema schema(String sdl, RuntimeWiring runtimeWiring) {
        SchemaGenerator.Options options = defaultOptions().captureAstDefinitions(true)
        return TestUtil.schema(options, sdl, runtimeWiring)
    }


    static GraphQLType unwrap(GraphQLType type) {
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

    static void commonSchemaAsserts(GraphQLSchema schema) {
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
        types[0].name == "Bar"
        types[1].name == "Foo"

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
        types[0].name == "Bar"
        types[1].name == "Foo"

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
        types[0].name == "Bar"
        types[1].name == "Foo"

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
        types[0].name == "Bar"
        types[1].name == "Foo"

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

        enumType.values.get(0).getValue() == "BLUE"
        enumType.values.get(1).getValue() == "GREEN"
        enumType.values.get(2).getValue() == "RED"

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

        schema.queryType.fieldDefinitions[0].name == "is_bar"
        schema.queryType.fieldDefinitions[0].type.name == "Boolean"
        schema.queryType.fieldDefinitions[1].name == "is_foo"
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
               extraField2 : Int
            }
            extend type BaseType implements Interface3 {
               extraField3 : ID
            }
            extend type BaseType {
               extraField4 : Boolean
            }
            extend type BaseType {
               extraField5 : Boolean!
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
        type.fieldDefinitions[0].name == "appearsIn"
        type.fieldDefinitions[1].name == "friends"
        type.fieldDefinitions[2].name == "homePlanet"
        type.fieldDefinitions[3].name == "id"
        type.fieldDefinitions[4].name == "name"

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

        def wiring = newRuntimeWiring()
                .type("Enum", { TypeRuntimeWiring.Builder it -> it.enumValues(enumValuesProvider) } as UnaryOperator)
                .build()
        def schema = schema(spec, wiring)
        GraphQLEnumType enumType = schema.getType("Enum") as GraphQLEnumType

        then:
        enumType.getValue("A").value == ExampleEnum.A
        enumType.getValue("B").value == ExampleEnum.B
        enumType.getValue("C").value == ExampleEnum.C
    }

    def " MapEnum values provider"() {
        given:
        def spec = '''
            enum Enum{
                A
                B
                C
            }
            
            type Query{
                field: Enum
            }
        '''

        when:
        def mapEnumProvider = new MapEnumValuesProvider([A: 11, B: 12, C: 13])
        def enumTypeWiring = newTypeWiring("Enum").enumValues(mapEnumProvider).build()
        def wiring = RuntimeWiring.newRuntimeWiring().type(enumTypeWiring).build()
        def schema = TestUtil.schema(spec, wiring)
        GraphQLEnumType graphQLEnumType = schema.getType("Enum") as GraphQLEnumType

        then:
        graphQLEnumType.getValue("A").value == 11
        graphQLEnumType.getValue("B").value == 12
        graphQLEnumType.getValue("C").value == 13
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
        def schema = schema(spec)
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

    def "specifiedBy directive is supported"() {
        given:
        def spec = """
        type Query {
            foo: MyScalar
        }
        "My scalar has a specifiedBy url"
        scalar MyScalar @specifiedBy(url: "myUrl.example")
        """
        when:
        def schema = schema(spec)
        GraphQLScalarType scalar = schema.getType("MyScalar") as GraphQLScalarType

        then:
        scalar.getSpecifiedByUrl() == "myUrl.example"
        scalar.getDescription() == "My scalar has a specifiedBy url"
    }

    def "specifiedBy requires an url "() {
        given:
        def spec = """
        type Query {
            foo: MyScalar
        }
        scalar MyScalar @specifiedBy
        """
        when:
        def registry = new SchemaParser().parse(spec)
        new SchemaGenerator().makeExecutableSchema(defaultOptions(), registry, TestUtil.mockRuntimeWiring)

        then:
        def schemaProblem = thrown(SchemaProblem)
        schemaProblem.message.contains("failed to provide a value for the non null argument 'url' on directive 'specifiedBy'")
    }

    def "specifiedBy can be added via extension"() {
        given:
        def spec = """
        type Query {
            foo: MyScalar
        }
        scalar MyScalar
        extend scalar MyScalar @specifiedBy(url: "myUrl.example")
        """
        when:
        def schema = schema(spec)
        GraphQLScalarType scalar = schema.getType("MyScalar") as GraphQLScalarType

        then:
        scalar.getSpecifiedByUrl() == "myUrl.example"
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

    def "nested additional types should be part of the additional types, not the schema types"() {
        def spec = """      
            type Query {
              fieldA : ReferencedA
            }
            
            type ReferencedA {
              field : String
            }

            type UnReferencedA {
              field : UnReferencedNestedE
            }
            
            input UnReferencedB {
              field : UnReferencedNestedF
            }
            
            type UnReferencedNestedE {
                field: String
                field2: UnReferencedScalarB
            }
            
            input UnReferencedNestedF {
                field: String
            }
            
            interface UnReferencedC {
                field : UnReferencedNestedE
            }
            
            union UnReferencedD = ReferencedA  
            
            scalar UnReferencedScalarA 
            
            scalar UnReferencedScalarB
        """

        def schema = schema(spec)

        expect: "all types to be registered"
        schema.getType("ReferencedA") instanceof GraphQLObjectType
        schema.getType("UnReferencedA") instanceof GraphQLObjectType
        schema.getType("UnReferencedB") instanceof GraphQLInputObjectType
        schema.getType("UnReferencedC") instanceof GraphQLInterfaceType
        schema.getType("UnReferencedD") instanceof GraphQLUnionType
        schema.getType("UnReferencedNestedE") instanceof GraphQLObjectType
        schema.getType("UnReferencedNestedF") instanceof GraphQLInputObjectType


        and: "unreferenced types should all be additional types"

        def namedTypes = schema.getAdditionalTypes() as Set<GraphQLNamedType>
        namedTypes.name.toSet() == ["UnReferencedA",
                                    "UnReferencedB",
                                    "UnReferencedC",
                                    "UnReferencedD",
                                    "UnReferencedNestedE",
                                    "UnReferencedNestedF",
                                    "UnReferencedScalarA",
                                    "UnReferencedScalarB"].toSet()
    }


    def "nested additional types recursive"() {
        def spec = """      
            type Query {
              fieldA : ReferencedA
            }
            
            type ReferencedA {
              field : String
            }

            type UnReferencedA {
              field : UnReferencedNestedB
            }

            type UnReferencedNestedB {
                field: UnReferencedNestedB
            }
        """

        def schema = schema(spec)

        expect: "all types to be registered"
        schema.getType("ReferencedA") instanceof GraphQLObjectType
        schema.getType("UnReferencedA") instanceof GraphQLObjectType
        schema.getType("UnReferencedNestedB") instanceof GraphQLObjectType

        and: "unreferenced types should all be additional types"

        def namedTypes = schema.getAdditionalTypes() as Set<GraphQLNamedType>
        namedTypes.name.toSet() == ["UnReferencedA", "UnReferencedNestedB"].toSet()
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
        Object arg1 = printAst(query.getFieldDefinition("field").getArgument("arg1").argumentDefaultValue.value as Node)
        Object arg2 = printAst(query.getFieldDefinition("field").getArgument("arg2").argumentDefaultValue.value as Node)

        expect:
        arg1 == "10"
        arg2 == "[20]"
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
        Object argNoDefault = query.getFieldDefinition("field").getArgument("argNoDefault").argumentDefaultValue.value

        expect:
        argNoDefault == null
    }

    def "object type directives are gathered and turned into runtime objects with arguments"() {
        def spec = """
            directive @directive1 on OBJECT
            directive @fieldDirective1 on FIELD_DEFINITION
            directive @directive2 on OBJECT
            directive @directive3 on OBJECT
            directive @directiveWithArgs(strArg: String, intArg: Int, boolArg: Boolean, floatArg: Float,nullArg: String) on OBJECT
            directive @fieldDirective2 on FIELD_DEFINITION
            
            type Query @directive1 {
              field1 : String @fieldDirective1
            }
            
            extend type Query @directive2 {
                field2 : String @fieldDirective2
            }

            extend type Query @directive3
            
            extend type Query @directiveWithArgs(strArg : "String", intArg : 1, boolArg : true, floatArg : 1.1, nullArg : null)
            
                
        """

        def schema = schema(spec)
        GraphQLObjectType type = schema.getType("Query") as GraphQLObjectType

        expect:
        type.getDirectives().size() == 4
        type.getDirectives()[0].name == "directive1"
        type.getDirectives()[0].getDefinition() != null
        type.getDirectives()[1].name == "directive2"
        type.getDirectives()[1].getDefinition() != null
        type.getDirectives()[2].name == "directive3"
        type.getDirectives()[2].getDefinition() != null

        // test that fields can have directives as well

        def field1 = type.getFieldDefinition("field1")
        field1.getDirectives().size() == 1
        def fieldDirective1 = field1.getDirectives()[0]
        fieldDirective1.getName() == "fieldDirective1"

        def field2 = type.getFieldDefinition("field2")
        field2.getDirectives().size() == 1
        def fieldDirective2 = field2.getDirectives()[0]
        fieldDirective2.getName() == "fieldDirective2"

        def directive = type.getAppliedDirectives()[3] as GraphQLAppliedDirective
        directive.name == "directiveWithArgs"
        directive.arguments.size() == 5

        directive.arguments[argIndex].name == argName
        directive.arguments[argIndex].type == argType
        printAst(directive.arguments[argIndex].argumentValue.value as Node) == argValue

        // arguments are sorted
        where:
        argIndex | argName    | argType        | argValue
        0        | "boolArg"  | GraphQLBoolean | "true"
        1        | "floatArg" | GraphQLFloat   | "1.1"
        2        | "intArg"   | GraphQLInt     | "1"
        3        | "nullArg"  | GraphQLString  | "null"
        4        | "strArg"   | GraphQLString  | "\"String\""

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
            directive @IFaceDirective on INTERFACE
            interface IFace @IFaceDirective {
                field1 : String
            }
            directive @OnionDirective on UNION 
            union Onion @OnionDirective = A | B
            
            directive @EnumValueDirective on ENUM_VALUE
            directive @NumbDirective on ENUM 
            enum Numb @NumbDirective {
                X @EnumValueDirective,
                Y
            }
            directive @PuterDirective on INPUT_OBJECT
            directive @InputFieldDirective on INPUT_FIELD_DEFINITION
            input Puter @PuterDirective {
                inputField : String @InputFieldDirective
            }
        """

        def schema = schema(spec)
        GraphQLDirectiveContainer container = schema.getType(typeName) as GraphQLDirectiveContainer

        expect:

        container.getAppliedDirective(directiveName) != null

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
        String arg = printAst(query.getFieldDefinition("field").getArgument("arg").argumentDefaultValue.value as Node)

        expect:
        arg == '{str : "string", num : 100}'
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

        schema.getCodeRegistry().getFieldVisibility() == fieldVisibility

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
            
            
            directive @directive on INTERFACE 
            interface IAgeAndHeight @directive {
                age : Int
            }

            directive @directiveField on FIELD_DEFINITION 
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
            directive @directive on UNION 
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
            directive @directive on ENUM
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

            directive @directive on INPUT_OBJECT
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
            directive @strDirective on ARGUMENT_DEFINITION    
            directive @secondDirective on ARGUMENT_DEFINITION    
            directive @intDirective(inception: Boolean) on ARGUMENT_DEFINITION    
            directive @thirdDirective on ARGUMENT_DEFINITION    
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
        def intAppliedDirective = argInt.getAppliedDirective("intDirective")
        intAppliedDirective.name == "intDirective"
        intAppliedDirective.arguments.size() == 1
        def directiveArg = intAppliedDirective.getArgument("inception")
        directiveArg.name == "inception"
        directiveArg.type == GraphQLBoolean
        printAst(directiveArg.argumentValue.value as Node) == "true"
        intDirective.getArgument("inception").argumentDefaultValue.value == null
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
        def options = defaultOptions()
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
        printAst(directive.getArgument("knownArg").argumentDefaultValue.value as Node) == '"defaultValue"'
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
        def options = defaultOptions()

        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def directiveTest1 = schema.getDirective("test1")
        GraphQLNonNull.nonNull(GraphQLBoolean).isEqualTo(directiveTest1.getArgument("include").type)
        directiveTest1.getArgument("include").argumentDefaultValue.value == null
        def appliedDirective1 = schema.getObjectType("Query").getFieldDefinition("f1").getAppliedDirective("test1")
        printAst(appliedDirective1.getArgument("include").argumentValue.value as Node) == "false"

        def directiveTest2 = schema.getDirective("test2")
        GraphQLNonNull.nonNull(GraphQLBoolean).isEqualTo(directiveTest2.getArgument("include").type)
        printAst(directiveTest2.getArgument("include").argumentDefaultValue.value as Node) == "true"
        def appliedDirective2 = schema.getObjectType("Query").getFieldDefinition("f2").getAppliedDirective("test2")
        printAst(appliedDirective2.getArgument("include").argumentValue.value as Node) == "true"
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
        def options = defaultOptions()
        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def directive = schema.getObjectType("Query").getFieldDefinition("f").getDirective("testDirective")
        directive.getArgument("knownArg1").type == GraphQLString
        printAst(directive.getArgument("knownArg1").argumentDefaultValue.value as Node) == '"defaultValue1"'
        def appliedDirective = schema.getObjectType("Query").getFieldDefinition("f").getAppliedDirective("testDirective")
        printAst(appliedDirective.getArgument("knownArg1").argumentValue.value as Node) == '"overrideVal1"'

        directive.getArgument("knownArg2").type == GraphQLInt
        printAst(directive.getArgument("knownArg2").argumentDefaultValue.value as Node) == "666"
        printAst(appliedDirective.getArgument("knownArg2").argumentValue.value as Node) == "666"

        directive.getArgument("knownArg3").type == GraphQLString
        directive.getArgument("knownArg3").argumentDefaultValue.value == null
        appliedDirective.getArgument("knownArg3").argumentValue.value == null
    }

    def "deprecated directive is implicit"() {
        def spec = """

            type Query {
                f1 : String @deprecated
                f2 : String @deprecated(reason : "Just because")
            }
        """

        def options = defaultOptions()

        when:
        def registry = new SchemaParser().parse(spec)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        def f1 = schema.getObjectType("Query").getFieldDefinition("f1")
        f1.getDeprecationReason() == "No longer supported" // spec default text

        def directive = f1.getDirective("deprecated")
        printAst(directive.getArgument("reason").argumentDefaultValue.value as Node) == '"No longer supported"'
        directive.validLocations().collect { it.name() } == [Introspection.DirectiveLocation.FIELD_DEFINITION.name()]

        def appliedDirective = f1.getAppliedDirective("deprecated")
        appliedDirective.name == "deprecated"
        appliedDirective.getArgument("reason").type instanceof GraphQLNonNull
        (appliedDirective.getArgument("reason").type as GraphQLNonNull).wrappedType == GraphQLString
        printAst(appliedDirective.getArgument("reason").argumentValue.value as Node) == '"No longer supported"'

        when:
        def f2 = schema.getObjectType("Query").getFieldDefinition("f2")

        then:
        f2.getDeprecationReason() == "Just because"

        def appliedDirective2 = f2.getAppliedDirective("deprecated")
        appliedDirective2.name == "deprecated"
        appliedDirective2.getArgument("reason").type instanceof GraphQLNonNull
        (appliedDirective2.getArgument("reason").type as GraphQLNonNull).wrappedType == GraphQLString
        printAst(appliedDirective2.getArgument("reason").argumentValue.value as Node) == '"Just because"'
        def directive2 = f2.getDirective("deprecated")
        printAst(directive2.getArgument("reason").argumentDefaultValue.value as Node) == '"No longer supported"'
        directive2.validLocations().collect { it.name() } == [Introspection.DirectiveLocation.FIELD_DEFINITION.name()]
    }

    def "does not break for circular references to interfaces"() {
        def spec = """
          interface MyInterface {
              interfaceField: MyNonImplementingType
          }

          type MyNonImplementingType {
              nonImplementingTypeField: MyImplementingType
          }

          type MyImplementingType implements MyInterface{
              interfaceField: MyNonImplementingType
          }

          type Query {
              hello: String
          }
      """

        def types = new SchemaParser().parse(spec)
        def wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", { typeWiring -> typeWiring.dataFetcher("hello", { env -> "Hello, world" }) })
                .type("MyInterface", { typeWiring -> typeWiring.typeResolver({ env -> null }) })
                .build()
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(types, wiring)
        expect:
        assert schema != null
    }

    def "enum object default values are handled"() {
        def spec = '''
            enum EnumValue {
                ONE, TWO, THREE
            }
            
            input InputType {
                value : EnumValue
            }
            
            type Query {
                fieldWithEnum(arg : InputType = { value : ONE } ) : String
            }
        '''
        def types = new SchemaParser().parse(spec)
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(types, TestUtil.mockRuntimeWiring)
        expect:
        assert schema != null
        def queryType = schema.getObjectType("Query")
        def fieldWithEnum = queryType.getFieldDefinition("fieldWithEnum")
        def arg = fieldWithEnum.getArgument("arg")
        printAst(arg.argumentDefaultValue.value as Node) == '{value : ONE}'
    }

    def "extensions are captured into runtime objects"() {
        def sdl = '''
            directive @directive1 on SCALAR
            ######## Objects
             
            type Query {
                foo : String
            }
            
            extend type Query {
                bar : String
            }

            extend type Query {
                baz : String
            }

            ######## Enums 
          
            enum Enum {
                A
            }
            
            extend enum Enum {
                B
            }

            ######## Interface 
            
            interface Interface {
                foo : String
            }

            extend interface Interface {
                bar : String
            }

            extend interface Interface {
                baz : String
            }
            
            ######## Unions 
            
            type Foo {
                foo : String
            }
            
            type Bar {
                bar : Scalar
            }

            union Union = Foo
            
            extend union Union = Bar
            
            ######## Input Objects 

            input Input {
                foo: String
            }
            
            extend input Input {
                bar: String
            }

            extend input Input {
                baz: String
            }

            extend input Input {
                faz: String
            }
            
            ######## Scalar 

            scalar Scalar
            
            extend scalar Scalar @directive1
        '''


        when:
        def wiringFactory = new MockedWiringFactory() {
            @Override
            boolean providesScalar(ScalarWiringEnvironment env) {
                return env.getScalarTypeDefinition().getName() == "Scalar"
            }

            @Override
            GraphQLScalarType getScalar(ScalarWiringEnvironment env) {
                def definition = env.getScalarTypeDefinition()
                return GraphQLScalarType.newScalar()
                        .name(definition.getName())
                        .definition(definition)
                        .extensionDefinitions(env.getExtensions())
                        .coercing(TestUtil.mockCoercing())
                        .build()
            }
        }

        def runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(wiringFactory)
                .build()

        def options = defaultOptions()

        def types = new SchemaParser().parse(sdl)
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(options, types, runtimeWiring)

        then:
        schema != null

        (schema.getType("Query") as GraphQLObjectType).getExtensionDefinitions().size() == 2

        (schema.getType("Enum") as GraphQLEnumType).getExtensionDefinitions().size() == 1

        (schema.getType("Interface") as GraphQLInterfaceType).getExtensionDefinitions().size() == 2

        (schema.getType("Union") as GraphQLUnionType).getExtensionDefinitions().size() == 1

        (schema.getType("Input") as GraphQLInputObjectType).getExtensionDefinitions().size() == 3

        // scalars are special - they are created via a WiringFactory - but this tests they are given the extensions
        (schema.getType("Scalar") as GraphQLScalarType).getExtensionDefinitions().size() == 1
    }

    def "schema extensions and directives can be generated"() {
        def sdl = '''

            directive @sd1 on SCHEMA
            directive @sd2 on SCHEMA
            directive @sd3 on SCHEMA

            schema @sd1 {
                query : Query
            }
            
            extend schema @sd2 {
                mutation : Mutation
            }
            
            extend schema @sd3 
            
            type Query {
                f : String
            }

            type Mutation {
                f : String
            }
        '''

        when:
        def typeDefinitionRegistry = new SchemaParser().parse(sdl)
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, TestUtil.mockRuntimeWiring)

        then:

        schema.getQueryType().name == 'Query'
        schema.getMutationType().name == 'Mutation'

        when:
        def directives = schema.getSchemaDirectives() // Retain for test coverage

        then:
        directives.size() == 3
        schema.getSchemaDirective("sd1") != null // Retain for test coverage
        schema.getSchemaDirective("sd2") != null // Retain for test coverage
        schema.getSchemaDirective("sd3") != null // Retain for test coverage

        when:
        def directivesMap = schema.getSchemaDirectiveByName() // Retain for test coverage
        then:
        directives.size() == 3
        directivesMap["sd1"] != null
        directivesMap["sd2"] != null
        directivesMap["sd3"] != null

        when:
        directives = schema.getDirectives()

        then:
        directives.size() == 10 // built in ones :  include / skip and deprecated
        def directiveNames = directives.collect { it.name }
        directiveNames.contains("include")
        directiveNames.contains("skip")
        directiveNames.contains("defer")
        directiveNames.contains("deprecated")
        directiveNames.contains("specifiedBy")
        directiveNames.contains("oneOf")
        directiveNames.contains("sd1")
        directiveNames.contains("sd2")
        directiveNames.contains("sd3")

        when:
        directivesMap = schema.getDirectivesByName()

        then:
        directivesMap.size() == 10 // built in ones
        directivesMap.containsKey("include")
        directivesMap.containsKey("skip")
        directivesMap.containsKey("defer")
        directivesMap.containsKey("deprecated")
        directivesMap.containsKey("oneOf")
        directivesMap.containsKey("sd1")
        directivesMap.containsKey("sd2")
        directivesMap.containsKey("sd3")
    }

    def "directive arg descriptions are captured correctly"() {
        given:
        def spec = '''
        type Query {
            foo: String
        }
        directive @MyDirective(
        """
            DOC
        """
        arg: String) on FIELD
        '''
        when:
        def schema = schema(spec)

        then:
        schema.getDirective("MyDirective").getArgument("arg").description == "DOC"
    }

    def "capture DirectiveDefinitions"() {
        given:
        def spec = '''
        type Query {
            foo: String
        }
        directive @MyDirective on FIELD
        '''
        when:
        def schema = schema(spec)
        def directiveDefinition = schema.getDirective("MyDirective").getDefinition()
        then:
        directiveDefinition != null
        directiveDefinition.getName() == "MyDirective"


    }

    def "directive with enum args"() {
        given:

        def spec = """
        directive @myDirective (
            enumArguments: [SomeEnum!] = []
        ) on FIELD_DEFINITION

        enum SomeEnum {
            VALUE_1
            VALUE_2
        }
        type Query{ foo: String }
        """
        when:
        def schema = schema(spec)
        def directive = schema.getDirective("myDirective")
        then:
        directive != null
        GraphQLTypeUtil.simplePrint(directive.getArgument("enumArguments").getType()) == "[SomeEnum!]"
        printAst(directive.getArgument("enumArguments").getArgumentDefaultValue().value as Node) == "[]"
    }

    def "scalar used as output is not in additional types"() {
        given:

        def spec = """
        scalar UsedScalar
        type Query{ foo: UsedScalar }
        """
        when:
        def schema = schema(spec)
        then:
        schema.getType("UsedScalar") != null
        schema.getAdditionalTypes().find { it.name == "UsedScalar" } == null
    }

    def "scalar used as input is not in additional types"() {
        given:

        def spec = """
        scalar UsedScalar
        input Input {
            foo: UsedScalar
        }
        type Query{ foo(arg: Input): String }
        """
        when:
        def schema = schema(spec)
        then:
        schema.getType("UsedScalar") != null
        schema.getAdditionalTypes().find { it.name == "UsedScalar" } == null
    }

    def "unused scalar is not ignored and provided as additional type"() {
        given:

        def spec = """
        scalar UnusedScalar
        type Query{ foo: String }
        """
        when:
        def schema = schema(spec)
        then:
        schema.getType("UnusedScalar") != null
        schema.getAdditionalTypes().find { it.name == "UnusedScalar" } != null
    }

    def "interface can be implemented with additional optional arguments"() {
        given:
        def spec = """
            interface Vehicle {
              name: String!
            }

            type Car implements Vehicle {
              name(charLimit: Int = 10): String!
            }
            type Query {
                car: Car
            }
        """
        when:
        def schema = schema(spec)
        then:
        (schema.getType("Car") as GraphQLObjectType).getFieldDefinition("name").getArgument("charLimit") != null
        (schema.getType("Car") as GraphQLObjectType).getInterfaces().size() == 1

        (schema.getType("Vehicle") as GraphQLInterfaceType).getFieldDefinition("name").getArguments().size() == 0

    }

    def "extended enums work as expected for arg values"() {

        given:
        def spec = """
        enum AuthRoles {
            USER
        }

        extend enum AuthRoles {
            AUTHENTICATED
        }

        directive @auth(if: AuthRoles) on FIELD_DEFINITION

        type Query {
            danger: String @auth(if: AUTHENTICATED)
        }
        """
        when:
        def schema = schema(spec)
        then:
        def enumType = schema.getType("AuthRoles") as GraphQLEnumType
        def listOfEnumValues = enumType.getValues().collect({ it.getValue() })
        listOfEnumValues.sort() == ["AUTHENTICATED", "USER"]
    }

    def "extended input objects work as expected for arg values"() {

        given:
        def spec = """
        input ArgInput {
            fieldA : String
        }

        extend input ArgInput {
            fieldB : String
        }

        directive @auth(if: ArgInput) on FIELD_DEFINITION

        type Query {
            danger: String @auth(if: { fieldB : "B"} )
        }
        """
        when:
        def schema = schema(spec)
        then:
        def inputType = schema.getType("ArgInput") as GraphQLInputObjectType
        def listOfEnumValues = inputType.getFieldDefinitions().collect({ it.getName() })
        listOfEnumValues.sort() == ["fieldA", "fieldB"]
    }

    def "shows that issue 2238 and 2290 - recursive input types on directives - has been fixed"() {
        def sdl1 = '''
        directive @test(arg: Recursive) on OBJECT

        input Recursive {
          deeper: Recursive
          name: String
        }
        type Test @test(arg: { deeper: {name: "test"}}){
          field: String
        }
        type Query {
            test : Test
        }
        '''
        when:
        GraphQLSchema schema = TestUtil.schema(sdl1)
        then:
        schema != null

        def sdl2 = '''
        input MyInput {
          a: String
          b: MyInput
        }
        directive @myDirective(x: MyInput) on FIELD_DEFINITION
        type Query {
          f: String @myDirective(x: {b: {a:"yada"}})
        }
        '''

        when:
        schema = TestUtil.schema(sdl2)
        then:
        schema != null
    }

    def "code registry default data fetcher is respected"() {
        def sdl = '''
            type Query {
                field :  String
            }
        '''

        DataFetcher df = { DataFetchingEnvironment env ->
            env.getFieldDefinition().getName().reverse()
        }

        DataFetcherFactory dff = new DataFetcherFactory() {
            @Override
            DataFetcher get(DataFetcherFactoryEnvironment environment) {
                return df
            }

            @Override
            DataFetcher get(GraphQLFieldDefinition fieldDefinition) {
                return df
            }
        }

        GraphQLCodeRegistry codeRegistry = newCodeRegistry()
                .defaultDataFetcher(dff).build()

        def runtimeWiring = newRuntimeWiring().codeRegistry(codeRegistry).build()

        def graphQL = TestUtil.graphQL(sdl, runtimeWiring).build()
        when:
        def er = graphQL.execute('{ field }')
        then:
        er.errors.isEmpty()
        er.data["field"] == "dleif"

    }

    def "custom scalars can be used in schema generation as directive args"() {
        def sdl = '''
            directive @test(arg: MyType) on OBJECT
            scalar MyType
            type Test @test(arg: { some: "data" }){
                field: String
            }
            type Query {
                field: Test
            }
        '''
        def runtimeWiring = RuntimeWiring.newRuntimeWiring().scalar(TestUtil.mockScalar("MyType")).build()
        when:
        def schema = TestUtil.schema(sdl, runtimeWiring)
        then:
        schema.getType("MyType") instanceof GraphQLScalarType
    }

    def "1498 - order of arguments should not matter"() {
        def sdl = '''
            input TimelineDimensionPredicate {
                s : String
            }
            
            input TimelineDimension {
                d : String
            }
            
            type TimelineEntriesGroup {
                e : String
            }
            
            interface Timeline {
               id: ID!
                entryGroups(groupBy: [TimelineDimension!]!, filter: TimelineDimensionPredicate): [TimelineEntriesGroup!]!
            }

            type ActualSalesTimeline implements Timeline {
                id: ID!
                entryGroups(groupBy: [TimelineDimension!]!, filter: TimelineDimensionPredicate): [TimelineEntriesGroup!]!
            }
            
            type Query {
                salesTimeLine : Timeline
            }
        '''

        when:
        def schema = TestUtil.schema(sdl)
        then:
        assert1498Shape(schema)

        when: "The arg order has been rearranged"
        sdl = '''
            input TimelineDimensionPredicate {
                s : String
            }
            
            input TimelineDimension {
                d : String
            }
            
            type TimelineEntriesGroup {
                e : String
            }
            
            interface Timeline {
               id: ID!
                entryGroups(filter: TimelineDimensionPredicate, groupBy: [TimelineDimension!]!): [TimelineEntriesGroup!]!
            }

            type ActualSalesTimeline implements Timeline {
                id: ID!
                entryGroups(groupBy: [TimelineDimension!]!, filter: TimelineDimensionPredicate): [TimelineEntriesGroup!]!
            }
            
            type Query {
                salesTimeLine : Timeline
            }
        '''

        schema = TestUtil.schema(sdl)
        then:
        assert1498Shape(schema)

    }

    static boolean assert1498Shape(GraphQLSchema schema) {
        def actualSalesTL = schema.getObjectType("ActualSalesTimeline")
        def entryGroupsField = actualSalesTL.getField("entryGroups")
        def groupByArg = entryGroupsField.getArgument("groupBy")
        def filterArg = entryGroupsField.getArgument("filter")

        GraphQLInputObjectType groupArgType = GraphQLTypeUtil.unwrapAllAs(groupByArg.getType())
        assert groupArgType.name == "TimelineDimension"

        GraphQLInputObjectType filterArgType = GraphQLTypeUtil.unwrapAllAs(filterArg.getType())
        assert filterArgType.name == "TimelineDimensionPredicate"
        true
    }


    def "comments as descriptions disabled"() {
        def sdl = '''
        type Query {
            # Comment
            test : String
            "Description"
            test2: String
        }
        '''
        when:
        def registry = new SchemaParser().parse(sdl)
        def options = defaultOptions().useCommentsAsDescriptions(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        schema.getQueryType().getFieldDefinition("test").getDescription() == null
        schema.getQueryType().getFieldDefinition("test2").getDescription() == "Description"
    }

    def "ast definition capture can be disabled"() {
        def sdl = '''
        type Query {
            test : String
        }
        
        extend type Query {
            test2 : Int
        }
        '''
        when:
        def registry = new SchemaParser().parse(sdl)
        def options = defaultOptions().captureAstDefinitions(false)
        def schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        schema.getQueryType().getDefinition() == null
        schema.getQueryType().getExtensionDefinitions() == []
        schema.getQueryType().getFieldDefinition("test").getDefinition() == null

        when:
        registry = new SchemaParser().parse(sdl)
        options = defaultOptions() // default is to capture them
        schema = new SchemaGenerator().makeExecutableSchema(options, registry, TestUtil.mockRuntimeWiring)

        then:
        options.isCaptureAstDefinitions()
        schema.getQueryType().getDefinition() != null
        schema.getQueryType().getExtensionDefinitions().size() == 1
        schema.getQueryType().getFieldDefinition("test").getDefinition() != null
    }


    def "classCastException when interface extension is before base and has recursion"() {
        given:
        def spec = '''
        # order is important, moving extension below type Foo will fix the issue
        extend type Foo implements HasFoo {
          foo: Foo
        }
        
        type Query {
          test: ID
        }
        
        interface HasFoo {
          foo: Foo
        }
        
        type Foo {
          id: ID
        }
        '''

        when:
        TestUtil.schema(spec)

        then:
        noExceptionThrown()
    }

    def "skip and include should be added to the schema only if not already defined"() {
        def sdl = '''
            "Directs the executor to skip this field or fragment when the `if`'argument is true."
            directive @skip(
                "Skipped when true."
                if: Boolean!
              ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT
              
            "Directs the executor to include this field or fragment only when the `if` argument is true"
            directive @include(
                "Included when true."
                if: Boolean!
              ) on FIELD | FRAGMENT_SPREAD | INLINE_FRAGMENT
              
            type Query {
                hello: String
            }
        '''
        when:
        def schema = TestUtil.schema(sdl)
        then:
        schema.getDirectives().findAll { it.name == "skip" }.size() == 1
        schema.getDirectives().findAll { it.name == "include" }.size() == 1

        and:
        def newSchema = GraphQLSchema.newSchema(schema).build()
        then:
        newSchema.getDirectives().findAll { it.name == "skip" }.size() == 1
        newSchema.getDirectives().findAll { it.name == "include" }.size() == 1
    }

    def "oneOf directive is available implicitly"() {
        def sdl = '''
            type Query {
                f(arg : OneOfInputType) : String
            }

            input OneOfInputType @oneOf {
                a : String
                b : String
            }
        '''

        when:
        def schema = TestUtil.schema(sdl)
        then:
        schema.getDirectives().findAll { it.name == "oneOf" }.size() == 1

        GraphQLInputObjectType inputObjectType = schema.getTypeAs("OneOfInputType")
        inputObjectType.isOneOf()
        inputObjectType.hasAppliedDirective("oneOf")
    }

    def "should throw IllegalArgumentException when withValidation is false"() {
        given:
        def sdl = '''
            type Query { hello: String }
        '''
        def options = SchemaGenerator.Options.defaultOptions().withValidation(false)

        when:
        new SchemaGenerator().makeExecutableSchema(options, new SchemaParser().parse(sdl), RuntimeWiring.MOCKED_WIRING)

        then:
        thrown(IllegalArgumentException)
    }
}
