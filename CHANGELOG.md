# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2025-10-05

### Added

- **Core Engine & API**
    - Introduced a type-safe `ProcessEngine<T>` interface.
    - Added fluent builder pattern for `ProcessEngineConfig` for robust engine setup.
    - New convenience factories like `ProcessEngineFactory.createTbbpm()` and `ProcessEngineFactory.createBpmn()`.
    - Enhanced `ProcessSource` with multiple factory methods (`fromCode`, `fromFile`, `fromContent`).
    - New `ProcessResult` with functional methods like `map`, `orElse`, and `onSuccess`.

- **Hot Deployment**
    - New `compileflow-deploy` module for hot deployment capabilities.
    - Added `FlowHotDeployer` service to manage automatic process updates.
    - Implemented `FileSystemChangeDetector` for monitoring local file changes.
    - Implemented `NacosChangeDetector` for monitoring Nacos configuration changes.

- **Spring Ecosystem**
    - Improved Spring Boot auto-configuration in `compileflow-spring-boot-autoconfigure`.
    - Simplified integration with a dedicated `compileflow-spring-boot-starter`.

- **Documentation**
    - Complete overhaul of all documentation with comprehensive guides.
    - Added detailed guides for configuration, resource management, hot deployment, and monitoring.
    - New API Reference section with clear usage examples.

### Changed

- **Architecture**
    - Re-architected the engine to use a hybrid model of Dependency Injection and an Event Bus for better modularity.
    - Decoupled engine providers using Java's `ServiceLoader` (SPI), allowing for extensions like BPMN and TBBPM.
- **Configuration**
    - Migrated from a flat configuration model to a structured, builder-based `ProcessEngineConfig`.
- **API**
    - The core `ProcessEngine.execute` and `trigger` methods now support typed DTOs for improved type safety.

### Fixed

- Resolved inconsistencies between documented API examples and the actual implementation.
- Ensured configuration properties in documentation match the codebase.

### Deprecated

- Older, non-type-safe `ProcessEngine` methods will be deprecated. Refer to the migration guide for details.

### Removed

- Removed several outdated and undocumented configuration options.

### Security

- No security changes in this release.

---

## [1.2.0] - 2024-XX-XX

For older versions, please refer to the [GitHub Releases](https://github.com/alibaba/compileflow/releases) page.

[Unreleased]: https://github.com/alibaba/compileflow/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/alibaba/compileflow/compare/v1.2.0...v2.0.0

