# Contributing to Ghost-PDF5

Thank you for helping improve Ghost-PDF5.

## Development Requirements

- Java 25
- Maven 3.9 or later
- Git

Run the full test suite before submitting a change:

```bash
mvn test
```

## Change Scope

- Keep each change focused on one responsibility.
- Preserve existing public method signatures and HTTP contracts unless a breaking change is explicitly discussed.
- Preserve the PDFBox migration behavior and resource-closing rules.
- Add or update tests for Controller, Service, Logic, DTO, and shared utility changes as appropriate.
- Follow the existing package boundaries and `docs/coding-guidelines.md`.
- Do not combine dependency upgrades, package renames, frontend migration, and feature work in one pull request.

## Fixtures and Assets

- Do not commit private documents, personal information, credentials, or company data.
- Use synthetic PDF, image, Markdown, and OCR fixtures.
- Record the source and intended use of bundled binary assets.
- Do not copy third-party pages, product interfaces, logos, or unverified sample files into the repository.

## Security

Do not report confidential vulnerability details in a public issue.
Follow `SECURITY.md` and use GitHub private vulnerability reporting when it is available.

## Pull Requests

Describe the purpose, behavioral impact, test result, and any compatibility consideration.
Keep unrelated formatting and generated-file changes out of the pull request.
