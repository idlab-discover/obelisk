Obelisk Main Project
=======================

Monorepo for all things related to Obelisk Definitive Edition.

# Repository structure

* `main`: current release branch
* `next`: all upcoming changes go into this branch. Additional feature branches are used for implementing bigger changes.
* `YY.0M`: release-specific branches. Used for bugfixing older releases. If no development work is ongoing for such a branch, it can be deleted (we can always recreate it from the latest micro for this release).

# Versioning strategy

![calver-YY](https://img.shields.io/badge/calver-YY.0M.MICRO-085f88)

We are applying the Calendar Versioning or [CalVer](https://calver.org) scheme, with a single version number for all core Obelisk artifacts built during the same release cycle: Maven artifacts, Typescript modules, Docker images, Helm Charts, etc... The public API for Obelisk follows a different versioning scheme (see [Backwards compatibility](#backwards-compatibility)).

## Rationale?

* SemVer does not work for Obelisk due to the volatile nature of the environment.
* CalVer allows for a more flexible approach, while providing us with a more descriptive version number (e.g. at a single glance, we can see that a user has an issue with a release from October last year).

# Backwards compatibility

As opposed to when using a SemVer strategy, the version number no longer conveys information on when backwards compatibility was broken. That is why we will be introducing an additional api-level version (e.g. `v3` currently) that only applies when calling the Obelisk HTTP API (the only place it matters, as this is our only external facing API). By specifying this api-level using an HTTP Header, users can select which of the available APIs to target.

We can have aliases for api-levels such as `stable` (last stable api-level) or `latest` (newest api-level) as a usability feature. When the client does not specify an api-level, we could default to `stable`.\
\
**Recommendation**: Long running applications should always specify a specific api-level (e.g. `v3` ) to ensure that the same API can be used, even when Obelisk promotes a new stable API, for as long as the specified api-level is supported!

# Collaboration conventions

## Maven multi-module project

All Java and Kotlin code should be part of the Maven multi-module project `obelisk-parent` (see pom.xml at root level).

Please respect the following naming conventions for the modules belonging to this parent:

| Module Name Prefix | Description | Module Root Package | Module Java module Name |
| -------------------|-------------| --------------------|-------------------------|
| `lib-`| For library modules that are used as dependencies in other modules. Library modules are never executed directly. | `idlab.obelisk.[name]` | `lib.[name]` |
| `int-` | For modules that implement a Microservice that has no public interface (e.g. internal data processing pipeline). | `idlab.obelisk.services.internal.[name]` | `internal.[name]` |
| `pub-` | For modules that implement a Microservice with a public interface (e.g. HTTP API services). | `idlab.obelisk.services.pub.[name]` | `pub.[name]` |
| `plugin-` | For modules that implement one of the factory interfaces of the Obelisk API (see `lib-definitions`). Plugin modules are used as dependencies in Microservice modules, similar to `lib-` modules, but are meant to be swappable. | `idlab.obelisk.plugins.[pluginType].[name]` | `plugin.[pluginType].[name]` |

Additional fixed prefixes could be used for non-Maven projects (e.g. `web`- for web applications).

External dependencies that are used in multiple modules (e.g. Vertx) should be declared in the pom.xml at root level, so that they can be used in the child modules without the need for specifying a version (with the goal of using consistent versions across modules).

# Docker-compose info

* Login to docker public once: `docker login`
* Login to gitlab docker once: `docker login gitlab.ilabt.imec.be:4567`
* Start stack with `./start.sh -h`
* web-apiconsole: <http://localhost:4200>
* web-catalog: <http://localhost:8000>
* Admin login with: `admin` : `""`
* mkdocs server: <http://localhost:8888>
* Stop stack with `./stop.sh`.

# Useful info
