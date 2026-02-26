# CLAUDE.md

## Project Overview

graphql-java is a production-ready Java implementation of the [GraphQL specification](https://spec.graphql.org/). It focuses strictly on GraphQL execution — JSON parsing, HTTP, and database access are out of scope.

## Build System

Gradle with wrapper. Always use `./gradlew`.

```bash
./gradlew build          # Full build (compile + test + checks)
./gradlew test           # Run all tests
./gradlew check          # All verification tasks
./gradlew javadoc        # Generate Javadoc
```

Build caching and parallel execution are enabled by default.

## Java Version

- **Build toolchain**: Java 21
- **Target/release**: Java 11 (source and bytecode compatibility)
- CI tests on Java 11, 17, and 21

## Testing

- **All tests are written in [Spock](https://spockframework.org/) (Groovy)**. Write new tests in Spock, not JUnit.
- Test sources: `src/test/groovy/graphql/`
- Test resources: `src/test/resources/`
- JMH benchmarks: `src/jmh/`

Run tests:
```bash
./gradlew test
```

## Code Style and Conventions

Formatting follows `graphql-java-code-style.xml` (IntelliJ). Full guidelines are in `coding-guidelines.md`. Key rules:

- **No wildcard imports** — use explicit imports only
- **Max 2 levels of indentation** — extract methods to reduce nesting
- **Early method exit** — return early instead of wrapping in `if(!cond)`
- **No inner classes** — every class gets its own file
- **Immutable data classes** with Builder pattern and `transform()` method
- Builder factory method: `newFoo()` (not `newBuilder()`). Builder setters: `foo(value)` (not `setFoo(value)`)
- **Use `graphql.Assert`** instead of `Objects.requireNonNull`
- **Use `@Public` and `@Internal`** annotations for API stability. Never use package-private or protected — use public + `@Internal`
- **No Optional** — use nullable values instead (legacy decision for consistency)
- Default collections: `ArrayList`, `LinkedHashSet`, `LinkedHashMap`
- Dependencies as instance fields with `@VisibleForTesting` for testability
- Static methods only for simple utils with no dependencies
- Keep streams simple — no nested stream maps; extract inner logic to methods
- Public API methods take `FooEnvironment` argument objects for future compatibility

## Nullability

JSpecify annotations with NullAway (via ErrorProne) in strict mode. Public API classes annotated with `@PublicApi` should use `@Nullable` from `org.jspecify.annotations` for nullable parameters and return types.

## Dependencies

**No new dependencies.** This is a firm project policy — dependency conflicts make adoption harder for users.

## Project Structure

```
src/main/java/graphql/       # Main source
src/main/antlr/               # ANTLR grammar files (GraphQL parser)
src/test/groovy/graphql/      # Spock tests (mirrors main structure)
src/test/java/                # Additional Java tests
src/jmh/                      # JMH performance benchmarks
```

Key packages under `graphql/`:
- `execution/` — query execution engine
- `language/` — AST definitions
- `parser/` — ANTLR-based parser
- `schema/` — GraphQL schema types and validation
- `validation/` — query validation
- `normalized/` — query normalization
- `introspection/` — introspection support
- `scalar/` — built-in scalar types

## Pre-commit Hooks

Set up local hooks with:
```bash
./scripts/setup-hooks.sh
```

Hooks check for Windows-incompatible filenames and files over 10MB.

## CI

GitHub Actions. PRs run build + test on Java 25 (Corretto) and verify Javadoc generation. File validation checks run on all PRs.
