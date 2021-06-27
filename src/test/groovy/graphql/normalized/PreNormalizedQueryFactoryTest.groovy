package graphql.normalized


import graphql.ParseAndValidate
import graphql.TestUtil
import graphql.language.Document
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import graphql.validation.ValidationError
import spock.lang.Specification

class PreNormalizedQueryFactoryTest extends Specification {

    def "pre-normalized query with variables and skip include"() {
        given:
        def schema = """
        type Query {
            pets: Pet
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
        }
        """

        String query = '''
      query($var1: Boolean!,$var2: Boolean!,$var3: Boolean!, $var4: Boolean!){
          pets {
                ... on Cat {
                    cat_not: name @skip(if:true)
                    cat_not: name @skip(if:$var1)
                    cat_yes_1: name @include(if:true)
                    cat_yes_2: name @skip(if:$var2)
              }
              ...@skip(if:$var3) @include(if:$var4) {
                ... on Dog @include(if:$var1) {
                    dog_no: name @include(if:false)
                    dog_no: name @include(if:$var1) @skip(if:$var2)  
                    dog_yes_1: name @include(if:$var1)
                    dog_yes_2: name @skip(if:$var2)
                }
              }
              ... on Pet @skip(if:$var1) {
                    not: name
              }
              ... on Pet @skip(if:$var2) {
                    pet_name: name
              }
          }}
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def preNormalizedQuery = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def tree = printTree(preNormalizedQuery)
        println String.join("\n", tree)
        then:
        true
    }

    def "merged field with different skip include"() {
        given:
        def schema = """
        type Query {
            hello:String
        }
        """

        String query = '''
      query($var1: Boolean!,$var2: Boolean!,$var3: Boolean!, $var4: Boolean!){
        ...@skip(if:$var3) { 
          hello @skip(if:$var1) @include(if:$var2)
          ... @include(if:$var4) {
            hello    
          }
        }
        ...{ 
          hello @skip(if:$var3) @include(if:$var4)
        }
      }
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def preNormalizedQuery = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def tree = printTree(preNormalizedQuery)
        println String.join("\n", tree)
        then:
        true
    }

    def "merged field with and without skip include "() {
        given:
        def schema = """
        type Query {
            hello:String
        }
        """

        String query = '''
      query($var1: Boolean!,$var2: Boolean!,$var3: Boolean!, $var4: Boolean!){
        ...{ 
          hello @skip(if:$var1) @include(if:$var2)
        }
        ...{ 
          hello @skip(if:$var3) @include(if:$var4)
        }
        ... {
            hello
        }
      }
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)
        assertValidQuery(graphQLSchema, query)
        Document document = TestUtil.parseQuery(query)
        when:
        def preNormalizedQuery = PreNormalizedQueryFactory.createPreNormalizedQuery(graphQLSchema, document, null)
        def tree = printTree(preNormalizedQuery)
        println String.join("\n", tree)
        then:
        true
    }


    List<String> printTree(PreNormalizedQuery query) {
        def result = []
        Traverser<PreNormalizedField> traverser = Traverser.depthFirst({ it.getChildren() });
        traverser.traverse(query.getTopLevelFields(), new TraverserVisitorStub<PreNormalizedField>() {
            @Override
            TraversalControl enter(TraverserContext<PreNormalizedField> context) {
                PreNormalizedField queryExecutionField = context.thisNode();
                result << queryExecutionField.printDetails()
                return TraversalControl.CONTINUE;
            }
        });
        result
    }

    private static void assertValidQuery(GraphQLSchema graphQLSchema, String query) {
        Parser parser = new Parser();
        Document document = parser.parseDocument(query);
        List<ValidationError> validationErrors = ParseAndValidate.validate(graphQLSchema, document);
        if (validationErrors.size() > 0) {
            println validationErrors
        }
        assert validationErrors.size() == 0
    }


}
