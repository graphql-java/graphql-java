The task is to annotate public API classes (marked with `@PublicAPI`) with JSpecify nullability annotations.

Note that JSpecify is already used in this repository so it's already imported.

If you see a builder static class, you can label it `@NullUnmarked` and not need to do anymore for this static class in terms of annotations.

## Batch Size and Prioritization

Annotate approximately 10 classes per batch for optimal context management. Start with interface/simple classes first, then tackle complex classes with builders. This helps identify patterns early.

## Exploration Phase

Before annotating, use `grep` to search for how each class is instantiated (e.g., `grep -r "new Comment"`) to understand which parameters can be null. Check constructor calls, method returns, and field assignments to inform your nullability decisions.

Analyze this Java class and add JSpecify annotations based on:
1. Set the class to be `@NullMarked`
2. Remove all the redundant `@NonNull` annotations that IntelliJ added
3. Check Javadoc @param tags mentioning "null", "nullable", "may be null"
4. Check Javadoc @return tags mentioning "null", "optional", "if available"
5. Method implementations that return null or check for null
6. GraphQL specification details (see details below)

## Pattern Examples

Here are concrete examples of common annotation patterns:

**Interface:**
```java
@PublicApi
@NullMarked
public interface MyInterface {
    // Methods inherit @NullMarked context
}
```

**Class with nullable field:**
```java
@PublicApi
@NullMarked
public class Comment {
    private final String content;
    private final @Nullable SourceLocation sourceLocation;
    
    public Comment(String content, @Nullable SourceLocation sourceLocation) {
        this.content = content;
        this.sourceLocation = sourceLocation;
    }
    
    public @Nullable SourceLocation getSourceLocation() {
        return sourceLocation;
    }
}
```

**Class with nullable return type:**
```java
@PublicApi
@NullMarked
public class Container {
    public @Nullable Node getChildOrNull(String key) {
        // May return null
        return children.get(key);
    }
}
```

**Builder with @NullUnmarked:**
```java
@PublicApi
@NullMarked
public class MyClass {
    @NullUnmarked
    public static class Builder {
        // No further annotations needed in builder
    }
}
```

## GraphQL Specification Compliance
This is a GraphQL implementation. When determining nullability, consult the GraphQL specification (https://spec.graphql.org/draft/) for the relevant concept. Key principles:

The spec defines which elements are required (non-null) vs optional (nullable). Look for keywords like "MUST" to indicate when an element is required, and conditional words such as "IF" to indicate when an element is optional.

If a class implements or represents a GraphQL specification concept, prioritize the spec's nullability requirements over what IntelliJ inferred.

## Validation Strategy

Run `./gradlew compileJava` after every 3-5 classes annotated, not just at the end. This catches issues early and makes debugging easier.

Finally, please check all this works by running the NullAway compile check.

If you find NullAway errors, try and make the smallest possible change to fix them. If you must, you can use assertNotNull. Make sure to include a message as well.

## Formatting Guidelines

Do not make spacing or formatting changes. Avoid adjusting whitespace, line breaks, or other formatting when editing code. These changes make diffs messy and harder to review. Only make the minimal changes necessary to accomplish the task.

## Cleaning up
Finally, can you remove this class from the JSpecifyAnnotationsCheck as an exemption

You do not need to run the JSpecifyAnnotationsCheck. Removing the completed class is enough.

Remember to delete all unused imports when you're done from the class you've just annotated.

## Generics Annotations

When annotating generic types and methods, follow these JSpecify rules:

### Type Parameter Bounds

The bound on a type parameter determines whether nullable type arguments are allowed:

| Declaration | Allows `@Nullable` type argument? |
|-------------|----------------------------------|
| `<T>` | ❌ No — `Box<@Nullable String>` is illegal |
| `<T extends @Nullable Object>` | ✅ Yes — `Box<@Nullable String>` is legal |

**When to use `<T extends @Nullable Object>`:**
- When callers genuinely need to parameterize with nullable types
- Example: `DataFetcherResult<T extends @Nullable Object>` — data fetchers may return nullable types

**When to keep `<T>`:**
- When the type parameter represents a concrete non-null object
- Even if some methods return `@Nullable T` (meaning "can be null even if T is non-null")
- Example: `Edge<T>` with `@Nullable T getNode()` — node may be null, but T represents the object type
