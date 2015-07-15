# graphql-java

#### Friendly warning: This library is currently under development and not yet stable.     

This is a GraphQL Java implementation based on the [specification](https://github.com/facebook/graphql) 
and the JavaScript [reference implementation](https://github.com/graphql/graphql-js).
 

**Status**: Parsing and executing are implemented. Validation is in place, but not complete.
 

### Overview

This is a Java Implementation of GraphQL. The library aims for real-life usage in production. 
  
It takes care of parsing and executing a GraphQL query. It doesn't take care of actually fetching any data:
Data comes from implementing callbacks.


### Hello World

This is the famous "hello world" in graphql-java: 

```java
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class HelloWorld {

    public static void main(String[] args) {
    
        GraphQLObjectType queryType = newObject()
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                        .staticValue("world")
                        .build())
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();
        Object result = new GraphQL(schema, "{hello}").execute().getResult();
        
        System.out.println(result);
        // Prints: {hello=world}
    }
}
```

  
### Manual
  
#### Schema definition

`GraphQLSchema.newSchema()` returns a new `Builder` to define a new Schema. All other types are created with the same pattern:
`newObject`, `newFieldDefinition` etc.

A full schema example (stolen from the js reference implementation): [StarWarsSchema](src/test/groovy/graphql/StarWarsSchema.java)

#### Executing a Query

To execute a Query against a Schema instantiate a new `GraphQL` Object with the appropriate arguments and then call `execute()`. 

Example: [GraphQL Test](src/test/groovy/graphql/GraphQLTest.groovy)

Complexer examples: [StarWars query tests](src/test/groovy/graphql/StarWarsQueryTest.groovy)


### 

### How to build it 

Just clone the repo and type 

```sh
./gradlew build
```

In build/libs you will find the jar file.

### Run the tests


```sh
./gradlew test
```



### Details

The implementation is in Java 7, but the tests are in Groovy and [Spock](https://github.com/spockframework/spock).

The query parsing is done with [ANTLR](www.antlr.org). The grammar is [here](src/main/grammar/Graphql.g4).

 
### Feedback

I would appreciate any feedback via Twitter [@andimarek](https://twitter.com/andimarek) or Pull request/Issue.


