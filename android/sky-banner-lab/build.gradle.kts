plugins {
    kotlin("jvm")
    application
}

sourceSets {
    named("main") {
        java.setSrcDirs(
            listOf(
                "src/main/kotlin",
                "../app/src/main/java"
            )
        )
        java.include(
            "com/bfalls/suntimealerts/tools/skybanner/**",
            "com/bfalls/suntimealerts/alarm/domain/model/**",
            "com/bfalls/suntimealerts/alarm/domain/service/**"
        )
        resources.srcDirs(
            "src/main/resources",
            "../app/src/main/res/drawable-nodpi"
        )
    }
    named("test") {
        java.setSrcDirs(listOf("src/test/kotlin"))
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.bfalls.suntimealerts.tools.skybanner.SkyBannerLabKt")
}

tasks.test {
    useJUnitPlatform()
}
