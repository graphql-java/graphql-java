package graphql

import graphql.execution.DataFetcherResult
import graphql.schema.DataFetcher
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLUnionType
import graphql.schema.TypeResolver
import graphql.schema.idl.FieldWiringEnvironment
import graphql.schema.idl.InterfaceWiringEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.UnionWiringEnvironment
import graphql.schema.idl.WiringFactory
import spock.lang.Specification

import static graphql.Assert.assertShouldNeverHappen
import static graphql.ExecutionInput.newExecutionInput
import static graphql.schema.GraphQLTypeUtil.unwrapAll
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class TypeResolverExecutionTest extends Specification {

    def simpleTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            // returns based on fields
            Map obj = env.object as Map
            if (obj.containsKey('topic')) {
                return env.getSchema().getObjectType('Conference')
            } else if (obj.containsKey('name')) {
                return env.getSchema().getObjectType('Concert')
            }
            return null
        }
    }

    def nullTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            // it couldn't find an appropriate type
            return null
        }
    }

    def aberrantTypeResolver = new TypeResolver() {
        @Override
        GraphQLObjectType getType(TypeResolutionEnvironment env) {
            // returns an irrelevant type that doesn't implement the interface
            return env.schema.getObjectType('OtherType')
        }
    }

    class SimpleTestWiringFactory implements WiringFactory {

        TypeResolver typeResolver

        SimpleTestWiringFactory(TypeResolver typeResolver) {
            this.typeResolver = typeResolver
        }

        @Override
        boolean providesTypeResolver(InterfaceWiringEnvironment environment) {
            return true
        }

        @Override
        TypeResolver getTypeResolver(InterfaceWiringEnvironment environment) {
            return typeResolver
        }

        @Override
        boolean providesTypeResolver(UnionWiringEnvironment environment) {
            return true
        }

        @Override
        TypeResolver getTypeResolver(UnionWiringEnvironment environment) {
            return typeResolver
        }

        @Override
        boolean providesDataFetcher(FieldWiringEnvironment environment) {
            if (unwrapAll(environment.fieldType) instanceof GraphQLInterfaceType ||
                    unwrapAll(environment.fieldType) instanceof GraphQLUnionType) {
                return true
            }
            return false
        }

        @Override
        DataFetcher getDataFetcher(FieldWiringEnvironment environment) {
            if (unwrapAll(environment.fieldType) instanceof GraphQLInterfaceType) {
                return { [id: 'confOne', topic: 'Front-End technologies'] }
            } else if (unwrapAll(environment.fieldType) instanceof GraphQLUnionType) {
                return { [id: 'getLucky', name: 'Daft Punk Anniversary'] }
            }
            assertShouldNeverHappen()
        }
    }

    def "happy case, type resolution should work"() {
        def idl = """
            type Query {
                event: Event
            }
            
            interface Event {
                id: String
            }
            
            type Concert implements Event {
                id: String
                name: String
            }
            
            type Conference implements Event {
                id: String
                topic: String    
            }
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(simpleTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    id 
                    ...on Conference { 
                        topic 
                    } 
                } 
            }''')

        then:
        def event = (res.data as Map)['event'] as Map
        event['id'] == 'confOne'
        event['topic'] == 'Front-End technologies'
        res.errors.empty
    }


    def "interface:  when typeResolver returns an aberrant type it should yield a GraphQL error"() {
        def idl = """
            type Query {
                event: Event
            }
            
            interface Event {
                id: String
            }
            
            type Concert implements Event {
                id: String
                name: String
            }
            
            type Conference implements Event {
                id: String
                topic: String    
            }
            
            type OtherType {
                id: String
            }
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(aberrantTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    id 
                    ...on Conference { 
                        topic 
                    } 
                } 
            }''')

        then:
        (res.data as Map)['event'] == null
        res.errors[0] instanceof UnresolvedTypeError
    }

    def "interface: when typeResolver returns an aberrant type and the field is non-nullable, it should yield a GraphQL error"() {
        def idl = """
            type Query {
                event: Event!
            }
            
            interface Event {
                id: String
            }
            
            type Concert implements Event {
                id: String
                name: String
            }
            
            type Conference implements Event {
                id: String
                topic: String    
            }
            
            type OtherType {
                id: String
            }
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(aberrantTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    id 
                    ...on Conference { 
                        topic 
                    } 
                } 
            }''')

        then:
        res.data == null
        res.errors[0] instanceof UnresolvedTypeError
    }


    def "interface: when typeResolver returns null (meaning it couldn't find an appropriate type), it should yield a UnresolvedTypeError GraphQL error"() {
        def idl = """
            type Query {
                event: Event
            }
            
            interface Event {
                id: String
            }
            
            type Concert implements Event {
                id: String
                name: String
            }
            
            type Conference implements Event {
                id: String
                topic: String    
            }
           
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(nullTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    id 
                    ...on Conference { 
                        topic 
                    } 
                } 
            }''')

        then:
        (res.data as Map)['event'] == null
        res.errors[0] instanceof UnresolvedTypeError
    }


    def "interface: when typeResolver returns null and the field is non-nullable, it should yield an UnresolvedTypeError GraphQL error"() {
        def idl = """
            type Query {
                event: Event!
            }
            
            interface Event {
                id: String
            }
            
            type Concert implements Event {
                id: String
                name: String
            }
            
            type Conference implements Event {
                id: String
                topic: String    
            }
           
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(nullTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    id 
                    ...on Conference { 
                        topic 
                    } 
                } 
            }''')

        then:
        res.data == null
        res.errors[0] instanceof UnresolvedTypeError
    }

    def "union:  when typeResolver returns an aberrant type it should yield a GraphQL error"() {
        def idl = """
            type Query {
                event: Event
            }
                        
            type Concert {
                id: String
                name: String
            }
            
            type Conference {
                id: String
                topic: String    
            }
            
            union Event = Concert | Conference
            
            type OtherType {
                id: String
            }
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(aberrantTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    ...on Conference { 
                        id
                        topic 
                    } 
                } 
            }''')

        then:
        (res.data as Map)['event'] == null
        res.errors[0] instanceof UnresolvedTypeError
    }

    def "union: when typeResolver returns an aberrant type and the field is non-nullable, it should yield a GraphQL error"() {
        def idl = """
            type Query {
                event: Event!
            }
                        
            type Concert {
                id: String
                name: String
            }
            
            type Conference {
                id: String
                topic: String    
            }
            
            union Event = Concert | Conference
            
            type OtherType {
                id: String
            }
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(aberrantTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    ...on Conference { 
                        id
                        topic 
                    } 
                } 
            }''')

        then:
        res.data == null
        res.errors[0] instanceof UnresolvedTypeError
    }


    def "union: when typeResolver returns null (meaning it couldn't find an appropriate type), it should yield a UnresolvedTypeError GraphQL error"() {
        def idl = """
            type Query {
                event: Event
            }
                        
            type Concert {
                id: String
                name: String
            }
            
            type Conference {
                id: String
                topic: String    
            }
            
            union Event = Concert | Conference
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(nullTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    ...on Conference { 
                        id
                        topic 
                    } 
                } 
            }''')

        then:
        (res.data as Map)['event'] == null
        res.errors[0] instanceof UnresolvedTypeError
    }


    def "union: when typeResolver returns null and the field is non-nullable, it should yield an UnresolvedTypeError GraphQL error"() {
        def idl = """
            type Query {
                event: Event!
            }
                        
            type Concert {
                id: String
                name: String
            }
            
            type Conference {
                id: String
                topic: String    
            }
            
            union Event = Concert | Conference
        """

        def runTimeWiring = RuntimeWiring.newRuntimeWiring()
                .wiringFactory(new SimpleTestWiringFactory(nullTypeResolver))
        def graphql = TestUtil.graphQL(idl, runTimeWiring).build()

        when:
        def res = graphql.execute('''
            { 
                event { 
                    ...on Conference { 
                        id
                        topic 
                    } 
                } 
            }''')

        then:
        res.data == null
        res.errors[0] instanceof UnresolvedTypeError
    }

    def "can get access to field selection set during type resolution"() {
        def sdl = '''

        type Query {
            foo : BarInterface
        }
        
        interface BarInterface {
             name : String
        }
         
        type NewBar implements BarInterface {
            name : String
            newBarOnlyField : String
        }

        type OldBar implements BarInterface {
            name : String
            oldBarOnlyField : String
        }
        '''

        TypeResolver typeResolver = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                boolean askedForOldBar = !env.getSelectionSet().getFields("oldBarOnlyField").isEmpty()
                if (askedForOldBar) {
                    return env.getSchema().getObjectType("OldBar")
                }
                return env.getSchema().getObjectType("NewBar")
            }
        }


        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("foo", { env -> [name: "NAME"] }))
                .type(newTypeWiring("BarInterface")
                        .typeResolver(typeResolver))
                .build()


        def graphQL = TestUtil.graphQL(sdl, wiring).build()

        when:
        def query = '''
        query {           
            foo {
                __typename
                ...OldBarFragment
                ...NewBarFragment
            }
        }

        fragment OldBarFragment on OldBar {
          name
          oldBarOnlyField
        }
        
        fragment NewBarFragment on NewBar {
          name
          newBarOnlyField
        }
        '''

        def er = graphQL.execute(query)

        then:
        er.errors.isEmpty()
        er.data == ["foo": [
                "__typename"     : "OldBar",
                "name"           : "NAME",
                "oldBarOnlyField": null,
        ]]


        when:
        query = '''
        query {           
            foo {
                __typename
                ...NewBarFragment
            }
        }

        fragment NewBarFragment on NewBar {
          name
        }
        '''

        er = graphQL.execute(query)

        then:
        er.errors.isEmpty()
        er.data == ["foo": [
                "__typename": "NewBar",
                "name"      : "NAME",
        ]]
    }

    def "can get access to attributes in the type resolver"() {
        def sdl = '''

        type Query {
            foo : BarInterface
        }
        
        interface BarInterface {
             name : String
        }
         
        type NewBar implements BarInterface {
            name : String
            newBarOnlyField : String
        }

        type OldBar implements BarInterface {
            name : String
            oldBarOnlyField : String
        }
        '''


        TypeResolver typeResolver = new TypeResolver() {
            @Override
            GraphQLObjectType getType(TypeResolutionEnvironment env) {
                assert env.getField().getName() == "foo"
                assert env.getFieldType() == env.getSchema().getType("BarInterface")
                assert env.getContext() == "Context"
                assert env.getGraphQLContext().get("x") == "graphqlContext"
                assert env.getLocalContext() == "LocalContext"
                return env.getSchema().getObjectType("NewBar")
            }
        }


        def df = { env ->
            DataFetcherResult.newResult().data([name: "NAME"]).localContext("LocalContext").build()
        }
        def wiring = RuntimeWiring.newRuntimeWiring()
                .type(newTypeWiring("Query")
                        .dataFetcher("foo", df))
                .type(newTypeWiring("BarInterface")
                        .typeResolver(typeResolver))
                .build()


        def graphQL = TestUtil.graphQL(sdl, wiring).build()

        def query = '''
        query {           
            foo {
                __typename
                ...NewBarFragment
            }
        }

        fragment NewBarFragment on NewBar {
          name
        }
        '''

        when:
        def ei = newExecutionInput(query)
                .context("Context")
                .graphQLContext(["x" : "graphqlContext"])
                .build()
        def er = graphQL.execute(ei)

        then:
        er.errors.isEmpty()
        er.data == ["foo": [
                "__typename": "NewBar",
                "name"      : "NAME",
        ]]
    }
}
