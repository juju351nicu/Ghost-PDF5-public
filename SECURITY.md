# Security Policy

## Current Scope

Ghost-PDF5 is currently intended for local development, learning, and portfolio use.
The current authentication, authorization, Cookie settings, upload controls, and operational monitoring
must not be treated as a production-ready Internet security boundary.

PDF, Markdown, and future OCR inputs can contain confidential or personal information.
Do not upload sensitive files to an instance operated by an untrusted party.

## External AI Processing (Optional)

Ghost-PDF5 can optionally send image data to an external vision model to transcribe an
uploaded image into Markdown, through the `POST /markdownDraftImage` endpoint. The provider
is selected by configuration (`ghost.ocr.provider`): `anthropic` (Anthropic Claude) or
`openai` (OpenAI). This feature is disabled by default and must be explicitly enabled through
the selected provider's configuration.

When enabled, the image bytes leave the local machine and are processed by the configured
provider under that provider's data-handling terms. Do not enable it for images that must
not leave your environment. Each provider's API key is read from an environment variable
(`ANTHROPIC_API_KEY` or `OPENAI_API_KEY` by default) and must never be committed or written
to logs. Review the selected provider's data retention and training policy before enabling.

## External Network Access (Optional)

Ghost-PDF5 can optionally fetch a web page from a URL supplied by the user, through the
`POST /markdownDraftUrl` endpoint, and convert its main content into a Markdown draft.
**This means the server makes an outbound HTTP request to a destination chosen by whoever can
call the endpoint.** The feature is disabled by default (`ghost.web.fetch.enabled=false`) and must
be enabled explicitly. `POST /markdownDraftHtml`, which converts an uploaded HTML file, performs no
network access and is unaffected by this setting.

To limit server-side request forgery (SSRF), every request — including **each redirect hop** — is
checked before the connection is made:

- Only the `http` and `https` schemes are accepted (`file`, `ftp`, `jar`, `data`, `gopher` are rejected).
- Only the ports in `ghost.web.fetch.allowed-ports` (80 and 443 by default) are accepted.
- URLs carrying user information (`https://user:pass@host/`) are rejected.
- Every address returned by name resolution is inspected, and the request is rejected if any one of
  them is a loopback, private, link-local (which covers the cloud metadata endpoint `169.254.169.254`),
  wildcard, multicast, or unique-local address. IPv4-mapped IPv6 addresses are judged by the mapped
  IPv4 address.
- Redirects are followed by the application itself (never by the HTTP client) up to
  `ghost.web.fetch.max-redirects`, re-running the checks above on every hop.
- Only `text/html` and `application/xhtml+xml` responses are processed.
- The response body is capped by the **number of bytes actually read** (`ghost.web.fetch.max-bytes`),
  not by the declared `Content-Length`.
- Connect and read timeouts, an explicit non-spoofed `User-Agent`, and a minimum interval between
  requests are applied. One request fetches exactly one URL; links are never followed automatically.

These checks are not complete against DNS rebinding: the addresses are validated at resolution time
and the connection is then made by host name, so a resolver that returns a different address on the
second lookup can still be reached. Closing that gap requires connecting to the validated IP directly
while re-implementing TLS host name verification, which is beyond the scope of this tool.

`ghost.web.fetch.allow-loopback` exists for development and for the automated tests that start a
local HTTP server. Keep it disabled in any other setting.

**Do not enable this feature on an instance operated by, or reachable by, an untrusted party.**
An endpoint that makes the server fetch arbitrary URLs is a network probe, and the checks above
reduce but do not eliminate that risk. Respect the terms of the sites you fetch: do not use this
feature for pages that require a login, for paid or access-restricted content, or where the site's
terms forbid automated retrieval.

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
