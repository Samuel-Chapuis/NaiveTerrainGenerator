# Commit Message Syntax

Commit messages must follow a clear and standardized convention to maintain the readability and traceability of the project. This standard is based on [Conventional Commits](https://www.conventionalcommits.org/) and aims to clarify the type of change, the context, and to make development more structured.

## General Format

- **type**: The type of change (see the list of types below).
- **scope** (optional): The part of the project affected by the change (e.g., a service, a module).
- **message**: A short description of the changes (in imperative mood and present tense).

## Types of Commits

- **feat**: Adding a new feature.
    - Example: `feat(user-auth): add login feature`

- **fix**: Bug fix.
    - Example: `fix(payment): resolve rounding issue in invoice calculation`

- **chore**: Minor tasks not affecting the application code (e.g., updating dependencies).
    - Example: `chore: update dependencies`

- **docs**: Documentation changes.
    - Example: `docs(readme): add usage instructions`

- **style**: Code style changes (formatting, indentation, etc.) with no functional impact.
    - Example: `style: fix indentation in codebase`

- **refactor**: Code refactoring without changing behavior.
    - Example: `refactor(order-service): simplify method flow`

- **test**: Adding or modifying tests.
    - Example: `test(user-service): add unit tests`

- **perf**: Performance improvements.
    - Example: `perf(api): improve database query performance`

- **ci**: Changes related to continuous integration or build tools.
    - Example: `ci: update GitHub Actions configuration`

## Best Practices

- **Clear and precise messages**: The message should be explicit and no longer than one line. If additional details are necessary, they can be included in the body of the commit.
- **Imperative present tense**: Use the imperative present tense to describe what the commit does, e.g., "add", "fix", "update".
- **Optional scope**: The scope is useful for indicating the part of the project affected by the commit but can be omitted if not relevant.
- **Avoid vague terms**: Avoid using terms like `update`, `change`, or `improve` which lack precision.

## Commit Examples

- **Adding a feature**: