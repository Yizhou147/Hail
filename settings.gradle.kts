// GitHub Actions 等 CI 环境网络正常，直接使用官方仓库；
// 仅本地构建（无 GITHUB_ACTIONS 环境变量）时优先使用国内镜像加速，
// 避免阿里云镜像在 CI 上返回 502 导致仓库被禁用、依赖解析失败。
// 注意：settings.gradle.kts 的 pluginManagement 块内无法引用脚本顶层 val
// （Gradle Kotlin DSL 作用域限制），故直接内联判断。
pluginManagement {
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == null) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GITHUB_ACTIONS") == null) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/central")
        }
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
    }
}
rootProject.name = "Hail"
include(":app")
