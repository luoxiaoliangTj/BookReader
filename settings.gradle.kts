pluginManagement {
    repositories {
        if (System.getenv("CI") != null) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/jcenter")
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("CI") != null) {
            google()
            mavenCentral()
            maven("https://jitpack.io")
        } else {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
            maven("https://jitpack.io")
        }
    }
}

rootProject.name = "BookReader"
include(":app")