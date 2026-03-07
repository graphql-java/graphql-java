# AI Agent Context for graphql-java

## Rules

- **Tests in Spock (Groovy)**, not JUnit: `src/test/groovy/graphql/`
- **No new dependencies** (firm policy)
- **No wildcard imports**, no inner classes, no `Optional`
- Max 2 indent levels; early returns; extract methods to reduce nesting
- Immutable data classes w/ Builder: `newFoo()` factory, `foo(value)` setters, `transform()` method
- Use `graphql.Assert` not `Objects.requireNonNull`
- Use `@Public`/`@Internal` annotations — never package-private/protected
- `@NullMarked` on all public API classes; `@NullUnmarked` on their Builder classes; use `@Nullable` for nullable params/returns; NullAway enforced via ErrorProne
- Full style guide: `coding-guidelines.md`

## Test Execution

When running tests, exclude the Java version-specific test tasks to avoid failures:

```bash
./gradlew test -x testWithJava21 -x testWithJava17 -x testWithJava11 -x testng
```
