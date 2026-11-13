# GitHub Copilot Instructions

## Code Style and Conventions

- Don't use fully qualified names for Java, Kotlin, or Groovy. Instead, add imports.
- Don't use wildcard imports. Please import items one by one instead. You can disable wildcard imports in your IDE
- Follow the code style defined in `graphql-java-code-style.xml`.

## Pull Request Review Guidelines

### Testing
- If you add new functionality, or correct a bug, you must also write a test so we can ensure your code works in the future
- If your pull request includes a performance improvement, please check in a JMH test to verify this. We'll then run a test on our isolated performance environment to verify the results
- 
### Breaking Changes
- Flag any breaking changes in public APIs so we can call this out in documentation
