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

We are aiming to not use Optional moving forward in order to be consistent overall.

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
- having a `transform` method


Every data class should be immutable and contain a `public static class Builder {..}` with a static factory method `newFoo` (not `newBuilder`).

The Builder methods are just named like the property (`Builder.foo(Foo foo)` not `Builder.setFoo(Foo foo)`). 

The class should also contain a `public Foo transform(Consumer<Builder> builderConsumer)` to allow for easy copies with minimal effort.

Private classes should follow the same design, but they don't have to.

### Default Collections idiom

The default pattern for using Set, Map and List is:
- `List<Foo> fooList = new ArrayList<>()`
- `Set<Foo> fooSet = new LinkedHashSet<>()`
- `Map<Foo> fooMap = new LinkedHashMap<>()`

By using the generic interface instead of using an implementation we are making sure we 
don't rely on anything impl specific.
The default implementations for `Set` and `Map` should be the `LinkedHashSet` and `LinkedHashMap` 
because it offers stable iteration order.

### Stream API vs for, index loop etc
Using the Stream API is ok in general, but it must kept simple. Stream maps inside 
maps should be avoided and the inner logic should be refactored into a method.   

It also ok to use the traditional for loop or other constructs: sometimes it is more readable than
the modern Stream API. The Stream API is not a replacement for all other loops/iterations.


### Maximum Indentation is two
One of the most important rules is to keep the number of indentations as low as possible.
In general the max number should be two. This means a for loop inside a condition is ok.
A condition inside a for loop inside a for loop is not.

Extracting a method is the easy way out.

### Early method exit
Exit the method early to avoid an indentation:

```java
public void foo() {
  if(cond) {
    return;
  }
  ...do something
}
```
is better than:

```java
public void foo() {
  if(!cond) {
    ...do something
  }
}
```

### Maximum line length and multi line statements 

We don't have a strict max line length.
But of course every statement should be limited. Not so much in terms of length but much more in terms
of what the statement does.

If a statement is multiple lines long it should be broken down into the same indentation level. 

For example this is ok:
```java
        return myMap
                .entrySet()
                .stream()
                .map(entry -> mapEntry(entry))
                .collect(Collectors.toList());
```
This is not ok:
```java
        return fooListOfList.stream().map(
                 fooList -> fooList.stream()
                        .sorted((x,y) -> sort(x,y))
                        .map(foo -> foo.getMyProp())
                        .collect(toList())
```
It has a lambda in streams in streams. The inside stream should be extracted in a extra method and each
method call should be on a new line:
```java
        return fooListOfList
                 .stream()
                 .map(this::mapFooList)
                 .collect(toList());
```

### Every class its own file: avoid inner classes and interfaces
Every class/interface should have its one file in general. 
Inner classes are almost never ok (especially public ones). Every class should have its own file to make it easier to read and explore the code.

### Use `graphql.Assert` instead of `Objects`
We maintain our own small set of Assert util methods. Don't use `Objects.requireNonNull` and others in order
to be consistent.

### `FooEnvironment` method arguments for public API
Don't use specific arguments for interface methods but rather a `FooEnvironment` argument. This ensures future
backwards compatibility when new inputs are added.



