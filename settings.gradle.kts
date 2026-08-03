// GitHub Actions 等 CI 环境网络正常，直接使用官方仓库；
// 仅本地构建（无 GITHUB_ACTIONS 环境变量）时优先使用国内镜像加速，
// 避免阿里云镜像在 CI 上返回 502 导致仓库被禁用、依赖解析失败。
val useMirror = System.getenv("GITHUB_ACTIONS") == null

pluginManagement {
    repositories {
        if (useMirror) {
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
        if (useMirror) {
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
