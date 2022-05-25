# Frequently Asked Questions

## Where should bugs, security issues, or enhancement requests be reported?
The Obelisk Catalog has a built-in [Issue Tracker]({{extra.contact.url}}) that you can use for communicating with the support team.

## How is Obelisk licensed?
Our goal is to open-source the Obelisk platform. The exact license type is to be determined.

## Can I run Obelisk on my own infrastructure?
This should be possible in the future. Obelisk is a complex platform with lots of moving parts and there is still some work that needs to be done with regard to streamlining the installation process. Use the [Issue Tracker]({{extra.contact.url}}) to contact us for discussing terms for short term solutions.

## I can't ingest or query data, my requests result in an exception with error code 429. How can I fix this?
Obelisk uses a rate limiting mechanism to protect the platform against malicious use (intended or unintended) and to enforce a fair use policy between the various clients.

You can check the status of these limits by browsing to the following page: {{extra.catalog.url}}/my/ratelimit.

Contact us using the [Issue Tracker]({{extra.contact.url}}) if your application cannot be supported with the default limits.