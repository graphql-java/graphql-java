package graphql.normalized

import graphql.TestUtil
import graphql.language.Document
import graphql.schema.GraphQLSchema
import graphql.util.TraversalControl
import graphql.util.Traverser
import graphql.util.TraverserContext
import graphql.util.TraverserVisitorStub
import spock.lang.Specification

class PreNormalizedQueryFactoryTest extends Specification {

    def "pre-normalized query with variables and skip include"() {
        given:
        def schema = """
        type Query {
            pets: Pet
            dog(id:ID): Dog 
        }
        interface Pet {
            name: String
        }
        type Cat implements Pet {
            name: String
        }
        type Dog implements Pet {
            name: String
            search(arg1: Input1, arg2: Input1, arg3: Input1): Boolean
        }
        input Input1 {
            foo: String
            input2: Input2
        }
        input Input2 {
            bar: Int
        }
        """

        String query = '''
      query($true: Boolean!,$false: Boolean!,$var1: Input2, $var2: Input1){
          dog(id: "123"){
            search(arg1: {foo: "foo", input2: {bar: 123}}, arg2: {foo: "foo", input2: $var1}, arg3: $var2) 
          }
          pets {
                ... on Cat {
                    cat_not: name @skip(if:true)
                    cat_not: name @skip(if:$true)
                    cat_yes_1: name @include(if:true)
                    cat_yes_2: name @skip(if:$false)
              }
                ... on Dog @include(if:$true) {
                    dog_no: name @include(if:false)
                    dog_no: name @include(if:$false) @skip(if:$true)  
                    dog_yes_1: name @include(if:$true)
                    dog_yes_2: name @skip(if:$false)
              }
              ... on Pet @skip(if:$true) {
                    not: name
              }
              ... on Pet @skip(if:$false) {
                    pet_name: name
              }
          }}
        '''
        GraphQLSchema graphQLSchema = TestUtil.schema(schema)

//        assertValidQuery(graphQLSchema, query, variables)
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


}
