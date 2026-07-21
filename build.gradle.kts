plugins {
    java
    `maven-publish`
    id("net.fabricmc.fabric-loom") version "1.15.4"
}

group = property("maven_group") as String
version = property("mod_version") as String

base {
    archivesName = property("archives_base_name") as String
}

repositories {
    maven("https://maven.caffeinemc.net/releases")
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.caffeinemc:sodium-fabric:${property("sodium_version")}")

    testImplementation(platform("org.junit:junit-bom:${property("junit_version")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

loom {
    runs {
        named("client") {
            client()
            configName = "Cinder Client"
            ideConfigGenerated(true)
            runDir("run")
        }

        register("fixtureClient") {
            client()
            configName = "Cinder Fixture Client"
            ideConfigGenerated(true)
            runDir("run")
            programArgs("--graphicsBackend", "VULKAN", "--vulkanValidation", "--renderDebugLabels")
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
    from("NOTICE") {
        rename { "${it}_${base.archivesName.get()}" }
    }
}

val prepareFixtureRun by tasks.registering(Copy::class) {
    from("fixtures/cinder-fixture") {
        into("shaderpacks/cinder-fixture")
    }
    from("fixtures/dev-config/cinder.json") {
        into("config")
    }
    into(layout.projectDirectory.dir("run"))
}

tasks.matching { it.name == "runFixtureClient" }.configureEach {
    dependsOn(prepareFixtureRun)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
