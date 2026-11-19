plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.11.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.2.3")
        instrumentationTools()
    }
    implementation("io.github.cdimascio:dotenv-java:3.0.0")
    implementation("com.mashape.unirest:unirest-java:1.4.9")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation(files("libs/taiti-conflicts.jar"))

    implementation("org.mongodb:mongodb-driver-sync:5.1.0")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
