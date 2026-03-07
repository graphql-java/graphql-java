# CLAUDE.md

Java GraphQL spec implementation. Build: `./gradlew build|test|check|javadoc`. Java 21 toolchain, targets Java 11.

## Rules

- **Tests in Spock (Groovy)**, not JUnit: `src/test/groovy/graphql/`
- **No new dependencies** (firm policy)
- **No wildcard imports**, no inner classes, no `Optional`
- Max 2 indent levels; early returns; extract methods to reduce nesting
- Immutable data classes w/ Builder: `newFoo()` factory, `foo(value)` setters, `transform()` method
- Use `graphql.Assert` not `Objects.requireNonNull`
- Use `@Public`/`@Internal` annotations — never package-private/protected
- `@NullMarked` on all public API classes; `@NullUnmarked` on their Builder classes; use `@Nullable` for nullable params/returns; NullAway enforced via ErrorProne
- Default collections: `ArrayList`, `LinkedHashSet`, `LinkedHashMap`
- Public API methods take `FooEnvironment` arg objects
- Full style guide: `coding-guidelines.md`
