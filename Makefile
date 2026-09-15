# Usage:
#   make bundle-release VERSION_NAME=0.0.8 VERSION_CODE=10
# VERSION_CODE must increase on every Play Store upload; last used was 9.
VERSION_NAME ?= 0.0.10
VERSION_CODE ?= 10

.PHONY: bundle-release
bundle-release:
	./gradlew bundleRelease -PversionNameOverride=$(VERSION_NAME) -PversionCodeOverride=$(VERSION_CODE) --console=plain 2>&1 | tail -10
