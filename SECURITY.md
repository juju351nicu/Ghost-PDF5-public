# Security Policy

## Current Scope

Ghost-PDF5 is currently intended for local development, learning, and portfolio use.
The current authentication, authorization, Cookie settings, upload controls, and operational monitoring
must not be treated as a production-ready Internet security boundary.

PDF, Markdown, and future OCR inputs can contain confidential or personal information.
Do not upload sensitive files to an instance operated by an untrusted party.

## Reporting a Vulnerability

Do not include secrets, personal information, private documents, or an executable proof of concept
in a public issue. Use GitHub's private vulnerability reporting feature when it is enabled for this repository.

Include the affected version or commit, the impacted endpoint or component, reproduction conditions,
and the expected security impact. Redact all uploaded document contents and credentials.

## Production Deployment

Before an Internet-facing deployment, review at least the following areas:

- Authentication and authorization.
- TLS and secure Cookie attributes.
- CSRF protection and session expiration.
- File type, byte size, page count, image dimension, and decompression limits.
- Temporary-file isolation and cleanup.
- Request timeout, rate limiting, and concurrency limits.
- Dependency and container vulnerability scanning.
- Audit logging without document contents or credentials.
- Storage encryption and retention policy.

The source repository being public does not mean that a running Ghost-PDF5 instance is safe for public access.
