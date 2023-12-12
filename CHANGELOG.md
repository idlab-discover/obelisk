# Changelog

All notable changes to this project will be documented in this file. See [conventional-commits.org](https://www.conventionalcommits.org/en/v1.0.0/#summary) for commit guidelines.

## [v23.12.0](https://github.com/idlab-discover/obelisk/compare/v23.6.0...v23.12.0) (2023-12-12)


### Features

* non transparent logo added to .assets ([208ff59](https://github.com/idlab-discover/obelisk/commit/208ff597c13cedd0d273257982e0a8f6e98cc458))
* remove docs v2 link ([#240](https://github.com/idlab-discover/obelisk/issues/240)) ([a730da1](https://github.com/idlab-discover/obelisk/commit/a730da1df1fafdb80796202790ff8be000b65978))

## [v23.6.0](https://github.com/idlab-discover/obelisk/compare/v23.4.0...v23.6.0) (2023-06-21)


### ⚠ BREAKING CHANGES

* **helm:** Move to single Helm chart for Obelisk stack

### Features

* **helm:** Move to single Helm chart for Obelisk stack ([18603c6](https://github.com/idlab-discover/obelisk/commit/18603c6581b53acc27954682cb221ce9d528c332))

## [v23.4.0](https://github.com/idlab-discover/obelisk/compare/v23.3.0...v23.4.0) (2023-04-12)


### Features

* web-console uses npm again ([6ad11bf](https://github.com/idlab-discover/obelisk/commit/6ad11bf85aedcbd365b27b2d001e9e2c75f9e334))

## [v23.3.1](https://github.com/idlab-discover/obelisk/compare/v23.3.0...v23.3.1) (2023-03-14)

### Features

* web-console uses npm again ([6ad11bf](https://github.com/idlab-discover/obelisk/commit/6ad11bf85aedcbd365b27b2d001e9e2c75f9e334))

## [v23.3.0](https://github.com/idlab-discover/obelisk/compare/v22.7.2...v23.3.0) (2023-03-13)

### Features

* **helm:** Obelisk deployment chart (single) ([#217](https://github.com/idlab-discover/obelisk/issues/217)) ([83ccb36](https://github.com/idlab-discover/obelisk/commit/83ccb366c1abf81ff51f501cd62107ea57d3aa82))
* **instrumentation:** improvements to instrumentation (added descriptions, rate limiting metrics, removed redundant global_*) ([d4bcce8](https://github.com/idlab-discover/obelisk/commit/d4bcce848146ce2d55c8493c06a4afc4d0267219))

### Bug Fixes

* compilation error regarding Nullable types ([a67a252](https://github.com/idlab-discover/obelisk/commit/a67a252a95c15e1fc0a13afc24e5bdefa6d24287))
* report PulsarClient TimeoutException at error level instead of warn ([50c57e4](https://github.com/idlab-discover/obelisk/commit/50c57e4c246e6d4a71ea3faa43dfddae61515fba))
* shutdown ingest-service when detecting a PulsarClient TimeoutException (as the service can't recover from this). ([2eb7e3a](https://github.com/idlab-discover/obelisk/commit/2eb7e3a0fa0441e6382b6d37bc10c69766810193))

## [v22.7.2](https://github.com/idlab-discover/obelisk/compare/v22.7.1...v22.7.2) (2022-11-17)

### Bug Fixes

* correct outgoing events instrumentation ([36cb7c8](https://github.com/idlab-discover/obelisk/commit/36cb7c8251e0de04c6a91c784d9e218866e7b11a))

## [v22.7.1](https://github.com/idlab-discover/obelisk/compare/v22.7.0...v22.7.1) (2022-07-29)

### Bug Fixes

* several data streaming and pub-monitor-service improvements ([36445cf](https://github.com/idlab-discover/obelisk/commit/36445cf143a55d364fb0f9a42f88232d90f4c162))
