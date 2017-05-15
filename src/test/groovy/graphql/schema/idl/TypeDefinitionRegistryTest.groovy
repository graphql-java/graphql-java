package graphql.schema.idl

import graphql.language.SchemaDefinition
import graphql.schema.idl.errors.SchemaProblem
import graphql.schema.idl.errors.SchemaRedefinitionError
import spock.lang.Specification

class TypeDefinitionRegistryTest extends Specification {

    TypeDefinitionRegistry parse(String spec) {
        new SchemaParser().parse(spec)
    }

    def "test default scalars are locked in"() {

        def registry = new TypeDefinitionRegistry()

        def scalars = registry.scalars()

        expect:

        scalars.containsKey("Int")
        scalars.containsKey("Float")
        scalars.containsKey("String")
        scalars.containsKey("Boolean")
        scalars.containsKey("ID")

        // graphql-java library extensions
        scalars.containsKey("Long")
        scalars.containsKey("BigInteger")
        scalars.containsKey("BigDecimal")
        scalars.containsKey("Short")
        scalars.containsKey("Char")

    }

    def "adding 2 schemas is not allowed"() {
        def registry = new TypeDefinitionRegistry()
        def result1 = registry.add(new SchemaDefinition())
        def result2 = registry.add(new SchemaDefinition())

        expect:
        !result1.isPresent()
        result2.get() instanceof SchemaRedefinitionError
    }


    def "merging multiple type registries does not overwrite schema definition"() {

        def spec1 = """ 
            schema {
                query: Query
            }
        """

        def spec2 = """ 
            type Post { id: Int! }
        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        def registry = result1.merge(result2)

        expect:
        result1.schemaDefinition().isPresent()
        registry.schemaDefinition().get().isEqualTo(result1.schemaDefinition().get())

    }

    def "test merge of schema types"() {

        def spec1 = """ 
            schema {
                query: Query
                mutation: Mutation
            }
        """

        def spec2 = """ 
            schema {
                query: Query2
                mutation: Mutation2
            }
        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:
        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0) instanceof SchemaRedefinitionError
    }

    def "test merge of object types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }

        """

        def spec2 = """ 
          type Post {
              id: Int!
            }

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:

        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0).getMessage().contains("tried to redefine existing 'Post'")
    }


    def "test merge of scalar types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }
            
            scalar Url

        """

        def spec2 = """ 
         
         scalar UrlX
         
         scalar Url
         

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:
        result1.merge(result2)

        then:

        SchemaProblem e = thrown(SchemaProblem)
        e.getErrors().get(0).getMessage().contains("tried to redefine existing 'Url'")
    }

    def "test successful merge of types"() {

        def spec1 = """ 
          type Post {
              id: Int!
              title: String
              votes: Int
            }

            extend type Post {
                placeOfPost : String
            }

        """

        def spec2 = """ 
          type Author {
              id: Int!
              name: String
              title : String
              posts : [Post]
            }
            
            extend type Post {
                timeOfPost : Int
            }

        """

        def result1 = parse(spec1)
        def result2 = parse(spec2)

        when:

        result1.merge(result2)

        then:

        noExceptionThrown()

        def post = result1.types().get("Post")
        def author = result1.types().get("Author")

        post.name == "Post"
        post.getChildren().size() == 3

        author.name == "Author"
        author.getChildren().size() == 4

        def typeExtensions = result1.typeExtensions().get("Post")
        typeExtensions.size() == 2
    }
}
