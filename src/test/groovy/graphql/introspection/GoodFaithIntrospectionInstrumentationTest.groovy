package graphql.introspection

import graphql.ExecutionResult
import graphql.TestUtil
import graphql.schema.FieldCoordinates
import spock.lang.Specification

class GoodFaithIntrospectionInstrumentationTest extends Specification {

    def graphql = TestUtil.graphQL("type Query { f : String }").instrumentation(new GoodFaithIntrospectionInstrumentation()).build()

    def "test asking for introspection in good faith"() {

        when:
        ExecutionResult er = graphql.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        then:
        er.errors.isEmpty()
    }

    def "test asking for introspection in bad faith"() {

        when:
        ExecutionResult er = graphql.execute(query)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospectionInstrumentation.BadFaithIntrospectionAbortExecutionException

        where:
        query                                                                                                    | _
        // long attack
        """
        query badActor{__schema{types{fields{type{fields{type{fields{type{fields{type{name}}}}}}}}}}}
        """                                                                                           | _
        // a case for __Type interfaces
        """ query badActor {
                __schema { types { interfaces { fields { type { interfaces { name } } } } } }
            }
        """                                                                                           | _
        // a case for __Type inputFields
        """ query badActor {
                __schema { types { inputFields { type { inputFields { name }}}}}
            }
        """                                                                                           | _
        // a case for __Type possibleTypes
        """ query badActor {
                __schema { types { inputFields { type { inputFields { name }}}}}
            }
        """                                                                                           | _
        // a case leading from __InputValue
        """ query badActor {
                __schema { types { fields { args { type { name fields { name }}}}}}
            }
        """                                                                                           | _
        // a case leading from __Field
        """ query badActor {
                __schema { types { fields { type { name fields { name }}}}}
            }
        """                                                                                           | _
        // a case for __type
        """ query badActor {
                __type(name : "t") { name }
                alias1 :  __type(name : "t1") { name }
            }
        """                                                                                           | _
        // a case for schema repeated - dont ask twice
        """ query badActor {
                __schema { types { name} }
                alias1 : __schema { types { name} }
            }
        """                                                                                           | _
    }

    def "test builder"() {
        GoodFaithIntrospectionInstrumentation goodFaithIntrospectionInstrumentation = GoodFaithIntrospectionInstrumentation.newGoodFaithIntrospection()
                .maximumUnderscoreSchemaInstances(2)
                .maximumUnderscoreTypeInstances(3)
                .maximumFieldInstances(FieldCoordinates.coordinates("__Type", "fields"), 2)
                .build()

        def graphql = TestUtil.graphQL("type Query { f : String }").instrumentation(goodFaithIntrospectionInstrumentation).build()

        when:
        def er = graphql.execute(IntrospectionQuery.INTROSPECTION_QUERY)
        then:
        er.errors.isEmpty()

        when:
        er = graphql.execute("""
            query ok { 
                __schema { types { name } }
                alias1: __schema { types { name } }
            }
        """)
        then:
        er.errors.isEmpty()

        when:
        er = graphql.execute("""
            query ok { 
                __schema { types { name } }
                alias1: __schema { types { name } }
                alias2: __schema { types { name } }
            }
        """)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospectionInstrumentation.BadFaithIntrospectionAbortExecutionException

        when:
        er = graphql.execute("""
            query ok { 
                __type(name : "Query") { name }
                alias1 : __type(name : "Query") { name }
                alias2 : __type(name : "Query") { name }
            }
        """)
        then:
        er.errors.isEmpty()

        when:
        er = graphql.execute("""
            query ok { 
                __type(name : "Query") { name }
                alias1 : __type(name : "Query") { name }
                alias2 : __type(name : "Query") { name }
                alias3 : __type(name : "Query") { name }
            }
        """)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospectionInstrumentation.BadFaithIntrospectionAbortExecutionException

        when:
        er = graphql.execute("""
            query ok { 
                __schema { types { fields { type { fields { name }}}}}
            }
        """)
        then:
        er.errors.isEmpty()

        when:
        er = graphql.execute("""
            query ok { 
                __schema { types { fields { type { fields { type { fields { name }}}}}}}
            }
        """)
        then:
        !er.errors.isEmpty()
        er.errors[0] instanceof GoodFaithIntrospectionInstrumentation.BadFaithIntrospectionAbortExecutionException
    }
}
