# graphql-java

[![Join the chat at https://gitter.im/graphql-java/graphql-java](https://badges.gitter.im/graphql-java/graphql-java.svg)](https://gitter.im/graphql-java/graphql-java?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

##### Friendly warning: As GraphQL itself is currently a Working Draft, expect changes.
     


This is a GraphQL Java implementation based on the [specification](https://github.com/facebook/graphql) 
and the JavaScript [reference implementation](https://github.com/graphql/graphql-js).
 

**Status**: Version `2.4.0` is released.
    
The versioning follows [Semantic Versioning](http://semver.org) since `2.0.0`. 

**Hint**: This README documents the latest release, but `master` contains the current development version. So please make sure 
to checkout the appropriate tag when looking for the version documented here.

[![Build Status](https://travis-ci.org/graphql-java/graphql-java.svg?branch=master)](https://travis-ci.org/graphql-java/graphql-java)
[![Latest Release](https://maven-badges.herokuapp.com/maven-central/com.graphql-java/graphql-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java/graphql-java/)
[![Latest Dev Build](https://api.bintray.com/packages/andimarek/graphql-java/graphql-java/images/download.svg)](https://bintray.com/andimarek/graphql-java/graphql-java/_latestVersion)



# Table of Contents
 
- [Overview](#overview)
- [Code of Conduct](#code-of-conduct)
- [Discussion](#discussion)
- [Hello World](#hello-world)
- [Getting started](#getting-started)
- [Manual](#manual)
    - [Schema definition](#schema-definition)
    - [Data fetching](#data-fetching)
    - [Executing](#executing)
    - [Execution strategies](#execution-strategies)
    - [Logging](#logging)
    - [Relay and Apollo Support](#relay-and-apollo-support)
- [Contributions](#contributions)
- [Build it](#build-it)
- [Development Build](#development-build)
- [Javadocs](#javadocs)
- [Details](#details)
- [Acknowledgment](#acknowledgment)
- [Related Projects](#related-projects)
- [License](#license)
 

### Overview

This is a Java Implementation of GraphQL. The library aims for real-life usage in production. 
  
It takes care of parsing and executing a GraphQL query. It doesn't take care of actually fetching any data:
Data comes from implementing callbacks or providing static data.

### Code of Conduct

Please note that this project is released with a [Contributor Code of Conduct](CODE_OF_CONDUCT.md).
By contributing to this project (commenting or opening PR/Issues etc) you are agreeing to follow this conduct, so please
take the time to read it. 


### Discussion

If you have a question or want to discuss anything else related to this project: 

- There is a mailing list (Google Group) for graphql-java: [graphql-java group](https://groups.google.com/forum/#!forum/graphql-java)
- And a chat room (Gitter.im) for graphql-java: [graphql-java chat](https://gitter.im/graphql-java/graphql-java)

### Hello World

This is the famous "hello world" in graphql-java: 

```java
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import java.util.Map;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

public class HelloWorld {

    public static void main(String[] args) {
        GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(newFieldDefinition()
                        .type(GraphQLString)
                        .name("hello")
                        .staticValue("world"))
                .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
                .query(queryType)
                .build();

        GraphQL graphQL = GraphQL.newGraphQL(schema).build();

        Map<String, Object> result = graphQL.execute("{hello}").getData();
        System.out.println(result);
        // Prints: {hello=world}
    }
}
```

### Getting started

##### How to use the latest release with Gradle

Make sure `mavenCentral` is among your repos:

```groovy
repositories {
    mavenCentral()
}

```
Dependency:

```groovy
dependencies {
  compile 'com.graphql-java:graphql-java:2.4.0'
}

```

##### How to use the latest release with Maven

Dependency:

```xml
<dependency>
    <groupId>com.graphql-java</groupId>
    <artifactId>graphql-java</artifactId>
    <version>2.4.0</version>
</dependency>

```
  
### Manual
  
#### Schema definition

##### Built-in Types

The `Scalars` Class has the following built-in types:

* `GraphQLString`
* `GraphQLBoolean`
* `GraphQLInt`
* `GraphQLFloat`

##### Creating a new Object Type

Example:
```java
GraphQLObjectType simpsonCharacter = newObject()
    .name("SimpsonCharacter")
    .description("A Simpson character")
    .field(newFieldDefinition()
            .name("name")
            .description("The name of the character.")
            .type(GraphQLString))
    .field(newFieldDefinition()
            .name("mainCharacter")
            .description("One of the main Simpson characters?")
            .type(GraphQLBoolean))
.build();

```

##### Creating a new Interface Type

Example:
```java
GraphQLInterfaceType comicCharacter = newInterface()
    .name("ComicCharacter")
    .description("A abstract comic character.")
    .field(newFieldDefinition()
            .name("name")
            .description("The name of the character.")
            .type(GraphQLString))
    .build();

```

##### Creating a new Union Type

Example: (a snippet from [here](src/test/groovy/graphql/GarfieldSchema.java))
```java
GraphQLUnionType PetType = newUnionType()
    .name("Pet")
    .possibleType(CatType)
    .possibleType(DogType)
    .typeResolver(new TypeResolver() {
        @Override
        public GraphQLObjectType getType(Object object) {
            if (object instanceof Cat) {
                return CatType;
            }
            if (object instanceof Dog) {
                return DogType;
            }
            return null;
        }
    })
    .build();
    
```

##### Creating a new Enum Type

Example:
```java
GraphQLEnumType colorEnum = newEnum()
    .name("Color")
    .description("Supported colors.")
    .value("RED")
    .value("GREEN")
    .value("BLUE")
    .build();
       
```

##### Creating a Object-Input Type

Example:

```java
GraphQLInputObjectType inputObjectType = newInputObject()
        .name("inputObjectType")
        .field(newInputObjectField()
                .name("field")
                .type(GraphQLString))
        .build();

```

##### Lists and NonNull

`GraphQLList` and `GraphQLNonNull` wrap another type to declare a list or to forbid null values. 

Example:

```java
        GraphQLList.list((GraphQLString); // a list of Strings

        GraphQLNonNull.nonNull(GraphQLString); // a non null String

        // with static imports its even shorter
        newArgument()
                .name("example")
                .type(nonNull(list(GraphQLString)));

```


##### Creating a Schema

Example:
```java
GraphQLSchema schema = GraphQLSchema.newSchema()
    .query(queryType) // must be provided
    .mutation(mutationType) // is optional
    .build();
            
```


A full schema example: [StarWarsSchema](src/test/groovy/graphql/StarWarsSchema.java)

Another schema example, including union types: [GarfieldSchema](src/test/groovy/graphql/GarfieldSchema.java)



##### Recursive Type References

GraphQL supports recursive Types: For example a `Person` can contain a list of friends of the same Type.
 
To be able to declare such a Type, `graphql-java` has a `GraphQLTypeReference` class.

When the schema is created, the `GraphQLTypeReference` is replaced with the actual real Type Object.

For example:

```java
GraphQLObjectType person = newObject()
    .name("Person")
    .field(newFieldDefinition()
            .name("friends")
            .type(new GraphQLList(new GraphQLTypeReference("Person"))))
    .build();

```
 
 
#### Data fetching

The actual data comes from `DataFetcher` objects.
 
Every field definition has a `DataFetcher`. When no one is configured, a 
[PropertyDataFetcher](src/main/java/graphql/schema/PropertyDataFetcher.java) is used.

`PropertyDataFetcher` fetches data from `Map` and Java Beans. So when the field name matches the Map key or
the property name of the source Object, no `DataFetcher` is needed. 



Example of configuring a custom `DataFetcher`:
```java

DataFetcher<Foo> fooDataFetcher = new DataFetcher<Foo>() {
    @Override
    public Foo get(DataFetchingEnvironment environment) {
        // environment.getSource() is the value of the surrounding
        // object. In this case described by objectType
        Foo value = perhapsFromDatabase(); // Perhaps getting from a DB or whatever 
        return value;
    }
};

GraphQLObjectType objectType = newObject()
        .name("ObjectType")
        .field(newFieldDefinition()
                .name("foo")
                .type(GraphQLString)
                .dataFetcher(fooDataFetcher))
        .build();

```

#### Executing 

To execute a Query/Mutation against a Schema build a new `GraphQL` Object with the appropriate arguments and then call `execute()`.
 
The result of a Query is a `ExecutionResult` Object with the result and/or a list of Errors.

Example: [GraphQL Test](src/test/groovy/graphql/GraphQLTest.groovy)

More complex examples: [StarWars query tests](src/test/groovy/graphql/StarWarsQueryTest.groovy)


#### Execution strategies

All fields in a SelectionSet are executed serially per default.
 
You can however provide your own execution strategies, one to use while querying data and one
to use when mutating data.

```java

ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
        2, /* core pool size 2 thread */
        2, /* max pool size 2 thread */
        30, TimeUnit.SECONDS,
        new LinkedBlockingQueue<Runnable>(),
        new ThreadPoolExecutor.CallerRunsPolicy());

GraphQL graphQL = GraphQL.newObject(StarWarsSchema.starWarsSchema)
        .queryExecutionStrategy(new ExecutorServiceExecutionStrategy(threadPoolExecutor))
        .mutationExecutionStrategy(new SimpleExecutionStrategy())
        .build();


```

When provided fields will be executed parallel, except the first level of a mutation operation.

See [specification](http://facebook.github.io/graphql/#sec-Normal-evaluation) for details.

Alternatively, schemas with nested lists may benefit from using a BatchedExecutionStrategy and creating batched DataFetchers with get() methods annotated @Batched.

#### JDK8 Lambdas
This project is built using JDK6. But if you're using JDK8 and above then you can also use lambdas.
```java
GraphQLObjectType queryType = newObject()
                .name("helloWorldQuery")
                .field(field -> field.type(GraphQLString)
                        .name("hello")
                        .argument(argument -> argument.name("arg")
                                .type(GraphQLBoolean))
                        .dataFetcher(env -> "hello"))
                .build();
```

#### Logging

Logging is done with [SLF4J](http://www.slf4j.org/). Please have a look at the [Manual](http://www.slf4j.org/manual.html) for details.
The `grapqhl-java` root Logger name is `graphql`.


#### Relay and Apollo Support

Very basic support for [Relay](https://github.com/facebook/relay) and [Apollo](https://github.com/apollographql/apollo-client) is included. While Apollo works with any schema, your schema will have to follow the Relay specification in order to work with Relay. Please look at https://github.com/andimarek/todomvc-relay-java for an example
project how to use it.

Relay and Apollo send queries to the GraphQL server as JSON containing a `query` field and a `variables` field. The `query` field is a JSON string,
and the `variables` field is a map of variable definitions. A relay-compatible server will need to parse this JSON and pass the `query`
string to this library as the query and the `variables` map as the third argument to `execute` as shown below. This is the implementation
from the [todomvc-relay-java](https://github.com/graphql-java/todomvc-relay-java) example.

```java
@RequestMapping(value = "/graphql", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
@ResponseBody
public Object executeOperation(@RequestBody Map body) {
    String query = (String) body.get("query");
    Map<String, Object> variables = (Map<String, Object>) body.get("variables");
    ExecutionResult executionResult = graphql.execute(query, (Object) null, variables);
    Map<String, Object> result = new LinkedHashMap<>();
    if (executionResult.getErrors().size() > 0) {
        result.put("errors", executionResult.getErrors());
        log.error("Errors: {}", executionResult.getErrors());
    }
    result.put("data", executionResult.getData());
    return result;
}
```

#### Contributions

Every contribution to make this project better is welcome: Thank you! 

In order to make this a pleasant as possible for everybody involved, here are some tips:

- Respect the [Code of Conduct](#code-of-conduct)
- Before opening an Issue to report a bug, please try the latest development version. It can happen that the problem is already solved.
- Please use  Markdown to format your comments properly. If you are not familiar with that: [Getting started with writing and formatting on GitHub](https://help.github.com/articles/getting-started-with-writing-and-formatting-on-github/)
- For Pull Requests:
  - Here are some [general tips](https://github.com/blog/1943-how-to-write-the-perfect-pull-request)
  - Please be a as focused and clear as possible  and don't mix concerns. This includes refactorings mixed with bug-fixes/features, see [Open Source Contribution Etiquette](http://tirania.org/blog/archive/2010/Dec-31.html) 
  - It would be good to add a automatic test. All tests are written in [Spock](http://spockframework.github.io/spock/docs/1.0/index.html).
   

#### Development Build

The latest development build is available on Bintray.

Please look at [Latest Build](https://bintray.com/andimarek/graphql-java/graphql-java/_latestVersion) for the 
latest version value.

#### Javadocs

See the [project page](http://graphql-java.github.io/graphql-java/) for the javadocs associated with each release.


#### How to use the latest build with Gradle

Add the repositories:

```groovy
repositories {
    mavenCentral()
    maven { url  "http://dl.bintray.com/andimarek/graphql-java" }
}

```

Dependency:

```groovy
dependencies {
  compile 'com.graphql-java:graphql-java:INSERT_LATEST_VERSION_HERE'
}

```


#### How to use the latest build with Maven

Add the repository:

```xml
<repository>
    <snapshots>
        <enabled>false</enabled>
    </snapshots>
    <id>bintray-andimarek-graphql-java</id>
    <name>bintray</name>
    <url>http://dl.bintray.com/andimarek/graphql-java</url>
</repository>

```

Dependency:

```xml
<dependency>
    <groupId>com.graphql-java</groupId>
    <artifactId>graphql-java</artifactId>
    <version>INSERT_LATEST_VERSION_HERE</version>
</dependency>

```







### Build it 

Just clone the repo and type 

```sh
./gradlew build
```

In `build/libs` you will find the jar file.

Running the tests:

```sh
./gradlew test
```

Installing in the local Maven repository:

```sh
./gradlew install
```



### Details

The implementation is in Java 6, but the tests are in Groovy and [Spock](https://github.com/spockframework/spock).

The query parsing is done with [ANTLR](http://www.antlr.org). The grammar is [here](src/main/antlr/Graphql.g4).

The only runtime dependencies are ANTLR and Slf4J.
 
### Acknowledgment

This implementation is based on the [js reference implementation](https://github.com/graphql/graphql-js).
For example the StarWarSchema and the tests (among a lot of other things) are simply adapted to the Java world.

### Related projects
* [todomvc-relay-java](https://github.com/graphql-java/todomvc-relay-java): Port of the Relay TodoMVC example to a java backend

* [graphql-java-type-generator](https://github.com/graphql-java/graphql-java-type-generator): This library will autogenerate GraphQL types for usage in com.graphql-java:graphql-java Edit

* [graphql-rxjava](https://github.com/nfl/graphql-rxjava): An execution strategy that makes it easier to use rxjava's Observable

* [graphql-java-reactive](https://github.com/bsideup/graphql-java-reactive): An execution strategy which is based on Reactive Streams. Project is evolving.

* [graphql-java-annotations](https://github.com/graphql-java/graphql-java-annotations): Annotations-based syntax for GraphQL schema definition.

* [graphql-java-servlet](https://github.com/graphql-java/graphql-java-servlet): Servlet that automatically exposes a schema dynamically built from GraphQL queries and mutations.

* [graphql-apigen](https://github.com/Distelli/graphql-apigen): Generate Java APIs with GraphQL Schemas

* [graphql-spring-boot](https://github.com/oembedler/graphql-spring-boot): GraphQL and GraphiQL Spring Framework Boot Starters

* [spring-graphql-common](https://github.com/oembedler/spring-graphql-common): Spring Framework GraphQL Library

* [graphql-jpa](https://github.com/jcrygier/graphql-jpa): JPA Implementation of GraphQL (builds on graphql-java)

* [graphql-jpa-spring-boot-starter](https://github.com/timtebeek/graphql-jpa-spring-boot-starter): Spring Boot starter for GraphQL JPA; Expose JPA entities with GraphQL.

* [graphkool](https://github.com/beyondeye/graphkool): GraphQl-java utilities in kotlin

* [schemagen-graphql](https://github.com/bpatters/schemagen-graphql): GraphQL-Java add-on that adds support for Schema Generation & Execution for enterprise level applications.

* [GraphQL-SPQR](https://github.com/leangen/GraphQL-SPQR): Java 8+ API for rapid development of GraphQL services

* [Light Java GraphQL](https://github.com/networknt/light-java-graphql): A lightweight, fast microservices framework with all other cross-cutting concerns addressed that is ready to plug in GraphQL schema. 

### License

graphql-java is licensed under the MIT License. See [LICENSE](LICENSE.md) for details.

Copyright (c) 2015, Andreas Marek and [Contributors](https://github.com/graphql-java/graphql-java/graphs/contributors)

[graphql-js License](https://github.com/graphql/graphql-js/blob/master/LICENSE)

