Thanks for contributing to graphql-java!


Please be sure that you read the [Code of Conduct](CODE_OF_CONDUCT.md) before contributing to this project and please
create a new Issue and discuss first what you are planning to do for larger changes.


The overall goal of graphql-java is to have a correct implementation of the [GraphQL Spec](https://github.com/facebook/graphql/) in a production ready way.

In order to achieve that we have a strong focus on maintainability and high test coverage:

- We expect new or modified unit test for every change (written in [Spock](https://spockframework.org/)).

- Your code should be formatted with our IntelliJ [graphql-java-code-style](graphql-java-code-style.xml).

- We don't add a new dependency to graphql-java: dependency conflicts will make adaption of graphql-java harder for users,
therefore we avoid adding any new dependency.

- graphql-java has a strict focus on executing a GraphQL request, this means JSON parsing, http communication, databases
access etc is out of scope.


## Git Hooks

This repository uses Git hooks to enforce code quality and compatibility standards. To install the hooks, run:

```bash
./scripts/setup-hooks.sh
```

The pre-commit hook will automatically check for:

- **Windows-incompatible filenames**: Files with characters that are reserved on Windows (< > : " | ? * \) will be rejected. This ensures the repository can be cloned on Windows systems.

- **Large files**: Files larger than 10MB will be rejected. If you need to commit large files, consider:
  - Splitting them into smaller parts (`.part1`, `.part2`, etc.)
  - Using [Git Large File Storage (LFS)](https://git-lfs.github.com/)
  - Reducing the file size

To bypass the hooks temporarily (not recommended), use `git commit --no-verify`.


If you have any question please consider asking in our [Discussions](https://github.com/graphql-java/graphql-java/discussions). For bug reports or specific code related topics create a new issue.

Thanks!


