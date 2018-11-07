# GraphQL Java Coding guidelines used in GraphQL Java


## General principles

- We prefer closer to zero dependencies. Dont bring in guava, apache-commons, spring-xxx, y or z however much some StringUtils method might be useful. Less dependencies makes graphql-java more applicable to everyone

- We prefer staying out of the HTTP stack. We are the low level engine of running graphql queries. Other concerns such as JSON and HTTP are handled better by other layers

- We prefer simple code to clever code. It should be readable with well named methods. Clever nested streams and lambdas are not our thing.

- We prefer general to specific. So the code should be generally applicable to use cases, not highly specific to just some use cases, even if it takes more setup.


## more specific topics

- Use `@Public` and `@Internal` to communicate what level of stability is supported. 

- Never make a class or method package private or protected: 
make it public or private and use `@Internal` to communicate that the class can be changed without notice. 
The user can decide itself about the risk when they use internal things.

### Optional vs null
We have a mix of Optional and allowing null values because GraphQL Java was originally written in Java 6.
We are not aiming to change the old code.

**TBD: Every new code should use Optional or not?**

### Unit testing and dependencies
All tests are written in [Spock](http://spockframework.org).

Every new code has to have unit tests. 

The general pattern is that every method of every class is by default non static and that every dependency is a instance field with package private visibility 
to allow for easy mocking in unit tests. The field should be annotated with `@VisibleForTesting`.

Example:

```java
public class Foo {

  @VisibleForTesting
  Bar bar = new Bar();


  public void doSomething(){
    ...
  }

} 

```

### Static methods 
Static methods are only allowed for methods which are very limited in functionality and don't have any dependencies. 
Static methods imply that you never want to mock them.

Typical examples are util methods like `GraphQLTypeUtil.isNonNull()` 

### "Util" class or not
Don't mix static and non static methods (except factory methods): 
every class is either a general "Util" class with only static methods or a class with no static methods.


### Naming
Naming is a key element of readable source code. 
Every variable and method should have a clear name. Single char variable names are never ok, except for index iterations.

### Comments
Public APIs should be documented via JavaDoc. The JavaDoc should describe how and why this class/method should be used. It should not the details of the implementation.

Internal APIs don't have JavaDoc and in general we avoid any form of comments when possible.

### Methods over comments
Most comments inside a method can be refactored by creating a method and giving the method name the comment text.

### Immutable and Builders
Every public data class should be:

- Immutable 
- having a Builder class 
- having a transform method


Every data class should be immutable and contain a `public static class Builder {..}` with a static factory method `newFoo` (not `newBuilder`).

The Builder methods are just named like the property (`Builder.foo(Foo foo)` not `Builder.setFoo(Foo foo)`). 

The class should also contain a `public Foo transform(Consumer<Builder> builderConsumer)` to allow for easy copies with minimal effort.

Private classes should follow the same design, but they don't have to.

