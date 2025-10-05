# How to Contribute to CompileFlow

We warmly welcome you to contribute to the CompileFlow project! We value every idea and contribution from the community.

This document provides a guide for contributing, which we hope will help you participate in the project's development
more smoothly.

## Code of Conduct

When participating in community discussions and contributions, please adhere to
our [Code of Conduct](../CODE_OF_CONDUCT.md). We are committed to building an open, friendly, and respectful community
environment.

## Reporting Issues

If you find a bug during use, or if you have any feature suggestions, feel free to submit them to us
via [GitHub Issues](https://github.com/alibaba/compileflow/issues).

To help us locate and resolve issues faster, please include as much of the following information as possible when
submitting an Issue:

* **Issue Type**: Bug, Feature Request, Documentation, or other?
* **CompileFlow Version**: The version number of `compileflow` you are using.
* **Problem Description**: A clear and detailed description of the problem you've encountered or your suggestion.
* **Reproduction Steps**: If it's a bug, please provide a minimal, stable set of steps to reproduce the problem.
* **Code Example**: Provide relevant process definition files and the Java code snippets used to trigger the execution.
* **Expected vs. Actual Behavior**: Describe what you expected to happen and what actually happened.
* **Environment Information**: JDK version, operating system, Spring Boot version, etc.

## Contributing Code (Pull Requests)

We very much welcome code contributions to the project via Pull Requests (PRs).

### Getting Started

1. **Fork the Repository**: Fork the `alibaba/compileflow` repository to your own GitHub account.
2. **Clone the Repository**: Clone your forked repository to your local machine:
   `git clone https://github.com/YOUR_USERNAME/compileflow.git`
3. **Create a Branch**: Create a new feature branch from the `master` branch:
   `git checkout -b feature/your-awesome-feature-name`. Please use a meaningful branch name.
4. **Code**: Make your code changes on the new feature branch.

### Code Style

* **Java**: We generally follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html),
  although there may be slight differences in some details. Please try to maintain consistency with the existing code
  style in the project.
* **Imports**: Please remove any unused `import` statements.
* **Javadoc**: Please add clear Javadoc comments for all new `public` methods and classes.

### Submitting a PR

1. **Commit Your Code**: `git commit -m "feat: Add some awesome feature"`. We recommend following
   the [Angular Commit Message Conventions](https://github.com/angular/angular/blob/main/CONTRIBUTING.md#commit).
    * `feat`: A new feature
    * `fix`: A bug fix
    * `docs`: Documentation changes
    * `style`: Code style changes (that do not affect code logic)
    * `refactor`: Code refactoring
    * `test`: Adding or modifying tests
    * `chore`: Changes to project configuration, build processes, etc.
2. **Stay in Sync**: Before submitting a PR, please pull the latest code from the upstream `master` branch and rebase
   your branch to ensure it is based on the latest code:
   ```bash
   git remote add upstream https://github.com/alibaba/compileflow.git
   git fetch upstream
   git rebase upstream/master
   ```
3. **Push Your Branch**: Push your feature branch to your own forked repository:
   `git push origin feature/your-awesome-feature-name`
4. **Create a PR**: On GitHub, create a Pull Request from your feature branch in your forked repository to the `master`
   branch of `alibaba/compileflow`.
5. **Describe the PR**: In the PR description, please clearly explain the purpose of the PR, what problem it solves, and
   your implementation plan. If it is related to an Issue, please use keywords like `Closes #123`.

### PR Review

After you submit a PR, the project maintainers will review your code as soon as possible. We may suggest some changes,
so please pay attention to the comments on the PR and participate in the discussion.

Once your PR passes the review and is merged into the main branch, your contribution officially becomes a part of
CompileFlow! Thank you for your hard work!

