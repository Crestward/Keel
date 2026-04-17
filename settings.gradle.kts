// SPDX-License-Identifier: AGPL-3.0-only
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.tensorflow.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiteRT-LM: com.google.ai.edge.litertlm:litertlm-android
        maven { url = uri("https://maven.google.com") }
    }
}

rootProject.name = "keel"

include(":app")
include(":core-model")
include(":core-database")
include(":core-datastore")
include(":feature-ingestion")
include(":feature-parser")
include(":feature-agent")
include(":feature-llm")
include(":feature-ui")
