pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "TorimaAuto"
include(":app")
include(":common")
include(":qa-updater")
project(":qa-updater").projectDir = file("common/qa-updater")
