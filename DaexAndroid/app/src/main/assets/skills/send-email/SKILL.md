---
name: send-email
description: Send an email using the native device email client.
---

# Send Email

## Instructions
Call the `runIntent` tool with the following parameters to launch the native email compose screen:

- intent: "send_email"
- parameters: A JSON string with the following fields:
  - extra_email: The recipient email address. String.
  - extra_subject: The subject line of the email. String.
  - extra_text: The main body text of the email. String.
