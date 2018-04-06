package graphql;


import graphql.execution.DeferringAsyncExecutionStrategy;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static org.junit.Assert.assertEquals;

public class Defer {

    private static GraphQL buildSimpleGraphQL() {
        GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                        .staticValue("world"))
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello2")
                        .staticValue("mars")
                )
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello3")
                        .staticValue("venus")
                )
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        return GraphQL.newGraphQL(schema).build();
    }

    private Map<String, Object> doQuery(String query, GraphQL graphQL, String test ) {
        ExecutionResult executionResult = graphQL.execute(query);
        assertEquals(0, executionResult.getErrors().size());
        Map<String, Object> result = executionResult.getData();
//        System.out.println(test + ": " + result.toString());
//        System.out.println(DeferringAsyncExecutionStrategy.getlastDeferredResults());
        return result;
    }

    @Test
    public void basicDeferLastTest() {
        Map<String, Object> result = doQuery("{\n" +
                "    hello\n" +
                "    hello2 @defer\n" +
                "}",
            buildSimpleGraphQL(), "basicDeferLastTest" );
        assertEquals(1, result.size());     // not 2, given @defer
        assertEquals("world", result.get("hello"));
    }

    @Test
    public void basicDeferFirstTest() {
        Map<String, Object> result = doQuery("{\n" +
                // test both variations (since one doesn't necessarily imply the other ...)
                        "    hello @defer\n" +
                        "    hello2\n" +
                        "}",
                buildSimpleGraphQL(), "basicDeferFirstTest" );
        assertEquals(1, result.size());     // not 2, given @defer
        assertEquals("mars", result.get("hello2"));
    }

    @Test
    public void basicDeferMiddleTest() {
        Map<String, Object> result = doQuery("{\n" +
                        "    hello3\n" +
                // another test that lays foundation for induction
                        "    hello @defer\n" +
                        "    hello2\n" +
                        "}",
                buildSimpleGraphQL(), "basicDeferMiddleTest" );
        assertEquals(2, result.size());
        assertEquals("mars", result.get("hello2"));
        assertEquals("venus", result.get("hello3"));
    }

    @Test
    public void basicDeferMiddleTest2() {
        Map<String, Object> result = doQuery("{\n" +
                // invert pattern of prior test
                        "    hello3 @defer\n" +
                        "    hello\n" +
                        "    hello2 @defer\n" +
                        "}",
                buildSimpleGraphQL(), "basicDeferMiddleTest" );
        assertEquals(1, result.size());
        assertEquals("world", result.get("hello"));
    }

    @Test
    public void emptyDeferTest() {
        Map<String, Object> result = doQuery("{\n" +
                "    hello @defer\n" +
                "}",
            buildSimpleGraphQL(), "emptyDeferTest" );
        assertEquals(0, result.size());
    }

    @Test
    public void empty2DeferTest() {
        Map<String, Object> result = doQuery("{\n" +
                "    hello @defer\n" +
                "    hello2 @defer\n" +
                "}",
            buildSimpleGraphQL(), "empty2DeferTest" );
        assertEquals(0, result.size());
    }

    @Test
    public void nestedDeferTest() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                "    hero {\n" +
                "        id\n" +
                "        name @defer\n" +
                "    }\n" +
                "}",
            GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "nestedDeferTest" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        assertEquals(1, hero.size());
    }

    @Test
    public void complexDeferFieldTest() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                "    hero {\n" +
                "        id\n" +
                "        name\n" +
                "        friends {\n" +
                // following will result in ..., friends=[{}, {}, {}]
                // that is, current implementation does not trim
                "            name @defer \n" +
                "        }\n" +
                "    }\n" +
                "}",
            GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "complexDeferFieldTest" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        List<Object> friends = (List<Object>) hero.get("friends");
        assertEquals(3, friends.size());
        Map<String, Object> friend1 = (Map<String, Object>) friends.get(0);
        // friends array that are each empty will be returned
        assertEquals(0, friend1.size());
        Map<String, Object> friend2 = (Map<String, Object>) friends.get(1);
        assertEquals(0, friend2.size());
        Map<String, Object> friend3 = (Map<String, Object>) friends.get(2);
        assertEquals(0, friend3.size());
    }

    @Test
    public void moreComplexDeferFieldTest() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                "   hero {\n" +
                "       id\n" +
                "       name\n" +
                "       friends {\n" +
                "           name @defer \n" +
                "           id" +
                "       }\n" +
                "   }\n" +
                "}",
                GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "moreComplexDeferFieldTest" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        List<Object> friends = (List<Object>) hero.get("friends");
        assertEquals(3, friends.size());
        Map<String, Object> friend1 = (Map<String, Object>) friends.get(0);
        assertEquals(1, friend1.size());
        assertEquals("1000", friend1.get("id"));
        Map<String, Object> friend2 = (Map<String, Object>) friends.get(1);
        assertEquals(1, friend2.size());
        assertEquals("1002", friend2.get("id"));
        Map<String, Object> friend3 = (Map<String, Object>) friends.get(2);
        assertEquals(1, friend3.size());
        assertEquals("1003", friend3.get("id"));
    }

    @Test
    public void moreComplexDeferFieldTest2() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                        "   hero {\n" +
                        "       id\n" +
                        "       name\n" +
                        "       friends {\n" +
                // check both variations, like above
                        "           name\n" +
                        "           id @defer\n" +
                        "       }\n" +
                        "   }\n" +
                        "}",
                GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "moreComplexDeferFieldTest2" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        List<Object> friends = (List<Object>) hero.get("friends");
        assertEquals(3, friends.size());
        Map<String, Object> friend1 = (Map<String, Object>) friends.get(0);
        assertEquals(1, friend1.size());
        assertEquals("Luke Skywalker", friend1.get("name"));
        Map<String, Object> friend2 = (Map<String, Object>) friends.get(1);
        assertEquals(1, friend2.size());
        assertEquals("Han Solo", friend2.get("name"));
        Map<String, Object> friend3 = (Map<String, Object>) friends.get(2);
        assertEquals(1, friend3.size());
        assertEquals("Leia Organa", friend3.get("name"));
    }

    @Test
    public void moreComplexDeferFieldTest3() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                        "   hero {\n" +
                        "       id\n" +
                        "       name\n" +
                        "       friends {\n" +
                        "           name\n" +
                        "           id @defer\n" +
                        "           appearsIn\n" +
                        "       }\n" +
                        "   }\n" +
                        "}",
                GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "moreComplexDeferFieldTest3" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        List<Object> friends = (List<Object>) hero.get("friends");
        assertEquals(3, friends.size());
        Map<String, Object> friend1 = (Map<String, Object>) friends.get(0);
        assertEquals(2, friend1.size());
        assertEquals("Luke Skywalker", friend1.get("name"));
        Map<String, Object> friend2 = (Map<String, Object>) friends.get(1);
        assertEquals(2, friend2.size());
        assertEquals("Han Solo", friend2.get("name"));
        Map<String, Object> friend3 = (Map<String, Object>) friends.get(2);
        assertEquals(2, friend3.size());
        assertEquals("Leia Organa", friend3.get("name"));
    }

    @Test
    public void moreComplexDeferFieldTest4() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                        "   hero {\n" +
                        "       id\n" +
                        "       name\n" +
                        "       friends {\n" +
                        "           name @defer\n" +
                        "           id\n" +
                        "           appearsIn @defer\n" +
                        "       }\n" +
                        "   }\n" +
                        "}",
                GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "moreComplexDeferFieldTest4" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        List<Object> friends = (List<Object>) hero.get("friends");
        assertEquals(3, friends.size());
        Map<String, Object> friend1 = (Map<String, Object>) friends.get(0);
        assertEquals("1000", friend1.get("id"));
        Map<String, Object> friend2 = (Map<String, Object>) friends.get(1);
        assertEquals(1, friend2.size());
        assertEquals("1002", friend2.get("id"));
        Map<String, Object> friend3 = (Map<String, Object>) friends.get(2);
        assertEquals(1, friend3.size());
        assertEquals("1003", friend3.get("id"));
    }

    @Test
    public void complexDeferObjectTest() {
        Map<String, Object> result = doQuery("query HeroNameAndFriendsQuery {\n" +
                "    hero {\n" +
                "        id\n" +
                "        name\n" +
                "        friends @defer {\n" +
                "            name\n" +
                "            id\n" +
                "        }\n" +
                "    }\n" +
                "}",
            GraphQL.newGraphQL(StarWarsSchema.starWarsSchema).build(), "complexDeferObjectTest" );
        assertEquals(1, result.size());
        Map<String, Object> hero = (Map<String, Object>) result.get("hero");
        assertEquals("2001", hero.get("id"));
        List<Object> friends = (List<Object>) hero.get("friends");
        //confirm there are no friends fields
        assertEquals(null, friends);
    }

}
