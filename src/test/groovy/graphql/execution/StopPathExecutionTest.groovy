package graphql.execution


import graphql.GraphQL
import graphql.TestUtil
import graphql.schema.DataFetcher
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import spock.lang.Specification

import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring

class StopPathExecutionTest extends Specification {

    def sdl = """
type Query {
   longList : [Thing]
   stopList : [Thing]
   thing : Thing
   stopThing : Thing
}

type Thing {
    name : String
    age : Int
    active : Boolean
}
"""

    def dataList = mkList()
    def thingo = ["name": "Theo", "age": 42, "active": true]

    DataFetcher<?> longListDF = env ->
            dataList
    DataFetcher<?> stopListDF = env ->
            DataFetcherResult.newResult().data(dataList).stopPathExecution(true).build()

    DataFetcher<?> thingDF = env -> thingo

    DataFetcher<?> stopThingDF = env ->
            DataFetcherResult.newResult().data(thingo).stopPathExecution(true).build()

    RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
            .type(
                    newTypeWiring("Query")
                            .dataFetcher("longList", longListDF)
                            .dataFetcher("stopList", stopListDF)
                            .dataFetcher("thing", thingDF)
                            .dataFetcher("stopThing", stopThingDF)
            )
            .build();
    GraphQLSchema graphQLSchema = TestUtil.schema(sdl, runtimeWiring)

    GraphQL graphQL = GraphQL.newGraphQL(graphQLSchema).build()

    private static List<Object> mkList() {
        List<Object> list = new ArrayList<>()
        for (int i = 0; i < 200; i++) {
            Map<String, Object> obj = new HashMap<>()
            obj.put("name", "Name" + i)
            obj.put("age", i)
            obj.put("active", i % 2 == 0)
            list.add(obj)
        }
        return list
    }

    def "can stop execution but get results"() {

        when:
        def er = graphQL.execute("""
            query q { 
                stopList { name age active }
                longList { name age active }
                thing { name age active }
                stopThing { name age active }
            }
            """)

        then:
        er.errors.isEmpty()
        def stopList = er.data["stopList"] as List
        stopList.size() == 200
        stopList[0]["name"] == "Name0"
        stopList[0]["age"] == 0
        stopList[0]["active"] == true

        def longList = er.data["longList"] as List
        longList.size() == 200
        longList[0]["name"] == "Name0"
        longList[0]["age"] == 0
        longList[0]["active"] == true

        def thing = er.data["thing"]
        thing["name"] == "Theo"
        thing["age"] == 42
        thing["active"] == true

        def stopThing = er.data["stopThing"]
        stopThing["name"] == "Theo"
        stopThing["age"] == 42
        stopThing["active"] == true
    }
}
