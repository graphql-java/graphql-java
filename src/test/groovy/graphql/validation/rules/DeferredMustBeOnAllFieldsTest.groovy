package graphql.validation.rules

import graphql.Directives
import graphql.GraphQL
import graphql.TestUtil
import graphql.validation.ValidationError
import graphql.validation.ValidationErrorType
import spock.lang.Specification

class DeferredMustBeOnAllFieldsTest extends Specification {

    def schema = TestUtil.schema('''
            type Query {
                newsFeed : NewsFeed
            }
            
            type NewsFeed {
                stories : [Story]
            }
            
            type Story {
                id : ID
                text : String
            }
           
        ''').transform({ it.additionalDirective(Directives.DeferDirective) })


    def "all fields MUST contain @defer on all declarations"() {

        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = """
             fragment StoryDetail on Story {
                id
                text @defer
              }
              query {
                newsFeed {
                  stories {
                    text @defer
                    
                    # fragment
                    ...StoryDetail
                    
                    ## inline fragment
                    ... on Story {
                        id
                        text # @defer is missing 
                    }
                  }
                }
              }        
          """


        when:
        def er = graphQL.execute(query)
        then:
        er.errors.size() == 1
        (er.errors[0] as ValidationError).validationErrorType == ValidationErrorType.DeferMustBeOnAllFields
        (er.errors[0] as ValidationError).queryPath == ["newsFeed", "stories"]

        when:
        query = """
             fragment StoryDetail on Story {
                id
                text # @defer is missing
              }
              query {
                newsFeed {
                  stories {
                    text @defer
                    
                    # fragment
                    ...StoryDetail
                    
                    ## inline fragment
                    ... on Story {
                        id
                        text @defer 
                    }
                  }
                }
              }        
          """

        er = graphQL.execute(query)

        then:
        er.errors.size() == 1
        (er.errors[0] as ValidationError).validationErrorType == ValidationErrorType.DeferMustBeOnAllFields
        (er.errors[0] as ValidationError).queryPath == ["newsFeed", "stories"]

        when:
        query = """
             fragment StoryDetail on Story {
                id
                text @defer
              }
              query {
                newsFeed {
                  stories {
                    text # @defer is missing
                    
                    # fragment
                    ...StoryDetail
                    
                    ## inline fragment
                    ... on Story {
                        id
                        text @defer 
                    }
                  }
                }
              }        
          """

        er = graphQL.execute(query)

        then:
        er.errors.size() == 1
        (er.errors[0] as ValidationError).validationErrorType == ValidationErrorType.DeferMustBeOnAllFields
        (er.errors[0] as ValidationError).queryPath == ["newsFeed", "stories"]

    }

    def "if all fields contain @defer then its ok"() {

        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = """
             fragment StoryDetail on Story {
                id
                text @defer
              }
              query {
                newsFeed {
                  stories {
                    text @defer
                    
                    # fragment
                    ...StoryDetail
                    
                    ## inline fragment
                    ... on Story {
                        id
                        text @defer 
                    }
                  }
                }
              }        
          """


        when:
        def er = graphQL.execute(query)
        then:
        er.errors.size() == 0
    }

    def "if only one field contain @defer then its ok"() {

        def graphQL = GraphQL.newGraphQL(schema).build()

        def query = """
             fragment StoryDetail on Story {
                id
                text @defer
              }
              query {
                newsFeed {
                  stories {
                    id
                    
                    # fragment
                    ...StoryDetail
                    
                    ## inline fragment
                    ... on Story {
                        id
                    }
                  }
                }
              }        
          """

        when:
        def er = graphQL.execute(query)
        then:
        er.errors.size() == 0
    }
}
