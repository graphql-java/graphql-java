# graphql-java

#### Friendly warning: As GraphQL itself is currently a Working Draft, expect changes.
     


This is a GraphQL Java implementation based on the [specification](https://github.com/facebook/graphql) 
and the JavaScript [reference implementation](https://github.com/graphql/graphql-js).
 

**Status**: Version 1.3 is released. 
    


**Hint**: This README documents the latest release, but `master` contains the current development version. So please make sure 
to checkout the appropriate tag when looking for the version documented here.

[![Build Status](https://travis-ci.org/andimarek/graphql-java.svg?branch=master)](https://travis-ci.org/andimarek/graphql-java)
[![Latest Release](https://maven-badges.herokuapp.com/maven-central/com.graphql-java/graphql-java/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.graphql-java/graphql-java/)
[![Latest Dev Build](https://api.bintray.com/packages/andimarek/graphql-java/graphql-java/images/download.svg)](https://bintray.com/andimarek/graphql-java/graphql-java/_latestVersion)



# Table of Contents
 
- [Overview](#overview)
- [Code of Conduct](#code-of-conduct)
- [Mailing List](#mailing-list)
- [Hello World](#hello-world)
- [Getting started](#getting-started)
- [Manual](#manual)
    - [Schema definition](#schema-definition)
    - [Data fetching](#data-fetching)
    - [Executing](#executing)
    - [Execution strategies](#execution-strategies)
    - [Logging](#logging)
    - [Relay Support](#relay-support)
- Contributions
- [Build it](#build-it)
- [Development Build](#development-build)
- [Details](#details)
- [Acknowledgment](#acknowledgment)
- [Feedback](#feedback)
- [License](#license)
 

### Overview

This is a Java Implementation of GraphQL. The library aims for real-life usage in production. 
  
It takes care of parsing and executing a GraphQL query. It doesn't take care of actually fetching any data:
Data comes from implementing callbacks or providing static data.

### Code of Conduct

Please note that this project is released with a [Contributor Code of Conduct](CODE_OF_CONDUCT.md).
By contributing to this project (commenting or opening PR/Issues etc) you are agreeing to follow this conduct, so please
take the time and read it. 


### Mailing List

If you have a question or discuss anything else: 

There is a mailing list (Google Group) for graphql-java: [graphql-java group](https://groups.google.com/forum/#!forum/graphql-java)

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
                        .name("helloWorldQuery")
                        .field(newFieldDefinition()
                                .type(GraphQLString)
                                .name("hello")
                                .staticValue("world")
                                .build())
                        .build();
        
        GraphQLSchema schema = GraphQLSchema.newSchema()
                        .query(queryType)
                        .build();
        Map<String, Object> result = new GraphQL(schema).execute("{hello}").getData();
        
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
  compile 'com.graphql-java:graphql-java:1.3'
}

```

##### How to use the latest release with Maven

Dependency:

```xml
<dependency>
    <groupId>com.graphql-java</groupId>
    <artifactId>graphql-java</artifactId>
    <version>1.3</version>
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
            .type(GraphQLString)
            .build())
    .field(newFieldDefinition()
            .name("mainCharacter")
            .description("One of the main Simpson characters?")
            .type(GraphQLBoolean)
            .build())                    
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
            .type(GraphQLString)
            .build())
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
        .type(GraphQLString)
        .build())
    .build()

```

##### Lists and NonNull

`GraphQLList` and `GraphQLNonNull` wrap another type to declare a list or to forbid null values. 

There are no builders to create now objects. Just normal constructors, because they are so simple.

Example:

```java
new GraphQLList(GraphQLString); // a list of Strings

new GraphQLNonNull(GraphQLString); // a non null String

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
            .type(new GraphQLList(new GraphQLTypeReference("Person")))
            .build())
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

DataFetcher calculateComplicatedValue = new DataFetcher() {
    @Override
    Object get(DataFetchingEnvironment environment) {
        // environment.getSource() is the value of the surrounding
        // object. In this case described by objectType
        Object value = ... // Perhaps getting from a DB or whatever 
        return value;
    }

GraphQLObjectType objectType = newObject()
    .name("ObjectType")
    .field(newFieldDefinition()
            .name("someComplicatedValue")
            .type(GraphQLString)
            .dataFetcher(calculateComplicatedValue)
            .build())
    .build();

```

#### Executing 

To execute a Query/Mutation against a Schema instantiate a new `GraphQL` Object with the appropriate arguments and then call `execute()`.
 
The result of a Query is a `ExecutionResult` Object with the result and/or a list of Errors.

Example: [GraphQL Test](src/test/groovy/graphql/GraphQLTest.groovy)

Complexer examples: [StarWars query tests](src/test/groovy/graphql/StarWarsQueryTest.groovy)


#### Execution strategies

All fields in a SelectionSet are executed serially per default. 

`GraphQL` takes as second constructor argument an optional [ExecutorService](http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ExecutorService.html).
When provided fields will be executed parallel, except the first level of a mutation operation.

See [specification](http://facebook.github.io/graphql/#sec-Normal-evaluation) for details.

It's recommended to use a `ExecutorService` to speed up execution.

Alternatively, schemas with nested lists may benefit from using a BatchedExecutionStrategy and creating batched DataFetchers with get() methods annotated @Batched.

#### Logging

Logging is done with [SLF4J](http://www.slf4j.org/). Please have a look at the [Manual](http://www.slf4j.org/manual.html) for details.
The `grapqhl-java` root Logger name is `graphql`.


#### Relay Support

There is a very basic Relay support included. Please look at https://github.com/andimarek/todomvc-relay-java for an example
project how to use it.

#### Development Build

The latest development build is available on Bintray.

Please look at [Latest Build](https://bintray.com/andimarek/graphql-java/graphql-java/_latestVersion) for the 
latest version value.


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

The implementation is in Java 7, but the tests are in Groovy and [Spock](https://github.com/spockframework/spock).

The query parsing is done with [ANTLR](http://www.antlr.org). The grammar is [here](src/main/grammar/Graphql.g4).

The only runtime dependencies are ANTLR and Slf4J.
 
### Acknowledgment

This implementation is based on the [js reference implementation](https://github.com/graphql/graphql-js).
For example the StarWarSchema and the tests (among a lot of other things) are simply adapted to the Java world.
 
### Feedback

I would appreciate any feedback via Twitter [@andimarek](https://twitter.com/andimarek) or Pull request/Issue.


### License

graphql-java is licensed under the MIT License. See [LICENSE](LICENSE.md) for details.

Copyright (c) 2015, Andreas Marek and [Contributors](https://github.com/andimarek/graphql-java/graphs/contributors)

[graphql-js License](https://github.com/graphql/graphql-js/blob/master/LICENSE)

