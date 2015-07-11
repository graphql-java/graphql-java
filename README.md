# graphql-java

This is a GraphQL Java implementation based on the [specification](https://github.com/facebook/graphql) 
and the JavaScript [reference implementation](https://github.com/graphql/graphql-js). 
   
It's an early version, but the query parser should be near 100%.

The execution part is WIP, and validation, error handling and more is still missing.
  
### How to use it
  
#### Schema definition

A complex schema (stolen from the js reference implementation): [StarWarsSchema](src/test/java/graphql/StarWarsSchema.java)

#### Query

For how to define a simple schema and execute queries: [GraphQL Test](src/test/groovy/graphql/GraphQLTest.groovy)


### Details

graphql-java is based on the same idea as the reference implementation: It assumes nothing about the persistence technology. 
Instead hooks/callbacks are provided to fetch the data.

The implementation is in Java 7, but the tests are in Groovy and [Spock](https://github.com/spockframework/spock).

The query parsing is done with [ANTLR](www.antlr.org). The grammar is [here](src/main/grammar/Graphql.g4).

 
### Feedback

I would appreciate any feedback via Twitter [@andimarek](https://twitter.com/andimarek) or Pull request/Issue.


