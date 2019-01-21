package graphql.schema.idl

import graphql.language.EnumTypeDefinition
import graphql.language.InterfaceTypeDefinition
import graphql.language.ObjectTypeDefinition
import graphql.language.ScalarTypeDefinition
import graphql.schema.idl.errors.SchemaProblem
import spock.lang.Specification
import spock.lang.Unroll

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
        TypeDefinitionRegistry typeRegistry = read(schema)
        then:
        typeRegistry.types().size() == 4
    }

    def assertSchemaProblem(String s) {
        try {
            read(s)
            assert false, "Expected a a schema problem for : " + s
        } catch (SchemaProblem ignored) {
            true
        }
    }

    def assertNoSchemaProblem(String s) {
        try {
            read(s)
            true
        } catch (SchemaProblem problem) {
            assert false, "Did not expected a schema problem for : " + s + " of : " + problem
        }
    }


    @Unroll
    def "empty types (with and without parentheses) are allowed in '#schema'"() {
        //
        // empty parentheses are not quite allowed by the spec but in the name of backwards compatibility
        // AND general usefulness we are going to allow them.  So in the list below the last two of each section
        // is not technically allowed by the latest spec
        //
        expect:
        assertNoSchemaProblem(schema)

        where:
        schema                               | _
        ''' type Foo '''                     | _
        ''' type Foo @directive '''          | _
        ''' type Foo { } '''                 | _
        ''' type Foo @directive { } '''      | _

        ''' interface Foo '''                | _
        ''' interface Foo @directive '''     | _
        ''' interface Foo { } '''            | _
        ''' interface Foo @directive { } ''' | _

        ''' input Foo '''                    | _
        ''' input Foo @directive '''         | _
        ''' input Foo { } '''                | _
        ''' input Foo @directive { } '''     | _

        ''' enum Foo '''                     | _
        ''' enum Foo @directive '''          | _
        ''' enum Foo { } '''                 | _
        ''' enum Foo @directive { } '''      | _

        ''' union Foo '''                    | _
        ''' union Foo @directive  '''        | _

        ''' scalar Foo '''                   | _  // special case - has no innards
    }


    @Unroll
    def "extensions are not allowed to be empty without directives in '#schema'"() {

        expect:
        assertSchemaProblem(schema)

        where:
        schema                         | _
        ''' extend type Foo'''         | _
        ''' extend type Foo {}'''      | _
        ''' extend interface Foo '''   | _
        ''' extend interface Foo {}''' | _
        ''' extend input Foo '''       | _
        ''' extend input Foo {}'''     | _
        ''' extend enum Foo '''        | _
        ''' extend enum Foo {}'''      | _
        ''' extend union Foo '''       | _
        ''' extend scalar Foo '''      | _
    }

    @Unroll
    def "extensions are allowed to be empty with directives in '#schema'"() {

        expect:
        assertNoSchemaProblem(schema)

        where:
        schema                                  | _
        ''' extend type Foo @d1 @d2 {}'''       | _
        ''' extend interface Foo @d1 @d2  {}''' | _
        ''' extend input Foo @d1 @d2 {}'''      | _
        ''' extend enum Foo @d1 @d2 {}'''       | _
        ''' extend union Foo @d1 @d2 '''        | _
        ''' extend scalar Foo @d1 @d2 '''       | _ // special case - has no innards
    }

    @Unroll
    def "extensions must extend with fields or directives in '#schema'"() {

        expect:
        assertNoSchemaProblem(schema)

        where:
        schema                                           | _
        ''' extend type Foo @directive'''                | _
        ''' extend type Foo { f : Int }'''               | _
        ''' extend type Foo @directive { f : Int }'''    | _

        ''' extend interface Foo @directive '''          | _
        ''' extend interface Foo { f : Int }'''          | _
        ''' extend interface Foo { f : Int }'''          | _

        ''' extend input Foo @directive '''              | _
        ''' extend input Foo { f : Int }'''              | _
        ''' extend input Foo { f : Int }'''              | _

        ''' extend enum Foo @directive '''               | _
        ''' extend enum Foo { a,b,c }'''                 | _
        ''' extend enum Foo @directive { a,b,c }'''      | _

        ''' extend union Foo @directive '''              | _
        ''' extend union Foo = | a | b | c'''            | _
        ''' extend union Foo = a | b | c'''              | _
        ''' extend union Foo @directive = | a | b | c''' | _
        ''' extend union Foo @directive = a | b | c'''   | _

        ''' extend scalar Foo @directive'''              | _ // special case - has no innards
    }
}
