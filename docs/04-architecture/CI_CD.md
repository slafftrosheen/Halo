# CI/CD Pipeline

Every pull request must run the full pipeline before review.

## Pipeline Steps
1. **Static Analysis**: Detekt, ktlint.
2. **Unit Tests**: Fast JVM tests.
3. **Instrumentation Tests**: Run against mock drivers.
4. **Build**: Compile all modules.
5. **API Compatibility Check**: Ensure Plugin, Driver, and Provider APIs are not broken.
6. **Documentation**: Generate KDoc and markdown validation.
7. **Artifact**: Produce signed debug APK/AAB.

## Regression Gates
Performance targets are regression gates, not goals:
* Cold start < 2s
* Idle battery drain < 1.5% per hour
* Foreground service RAM < 200MB
