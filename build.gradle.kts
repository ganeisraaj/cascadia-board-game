import edu.udo.cs.sopra.util.addFileToDistribution
import edu.udo.cs.sopra.util.sonatypeSnapshots
import edu.udo.cs.sopra.util.sopraPackageRegistry
import org.gradle.kotlin.dsl.application

plugins {
    kotlin("jvm") version "2.3.0"
    application
    id("edu.udo.cs.sopra") version "1.0.4"
}

group = "edu.udo.cs.sopra"
version = "1.0"

/* Change this to the version of the BGW you want to use */
val bgwVersion = "0.11"

kotlin {
    jvmToolchain(11)
}

application {
    mainClass.set("MainKt")
}

tasks.register<JavaExec>("runBot") {
    group = "application"
    description = "Runs the bot experiment from BotRun.kt"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("BotRunKt")
}

repositories {
    mavenCentral()
    sonatypeSnapshots()
    sopraPackageRegistry()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    // Source: https://mvnrepository.com/artifact/tools.jackson.module/jackson-module-kotlin
    implementation("tools.jackson.module:jackson-module-kotlin:3.2.0")
    implementation("tools.aqua:bgw-gui:$bgwVersion")
    implementation("tools.aqua:bgw-net-common:$bgwVersion")
    implementation("tools.aqua:bgw-net-client:$bgwVersion")
    implementation("edu.udo.cs.sopra:ntf:26B.1.0")
    // Add MockK for unit testing network layers
    testImplementation("io.mockk:mockk:1.13.12")
}

/* This is how you can add the how_to_play.pdf to the distribution zip file */
addFileToDistribution(file("./how_to_play.pdf"))

/* This is how you can ignore additional classes from test coverage */
/* All classes in gui, entity and service.bot package are already excluded. */

/* To ignore a class Foo in the package foo.bar.baz you would use the following line */
// this.ignoreClassesInCoverageReport("foo.bar.baz.Foo")

/* To ignore all classes in the foo.bar.baz package use a wildcard like this */
// this.ignoreClassesInCoverageReport("foo.bar.baz.*")

tasks.clean {
    delete.add("public")
}
