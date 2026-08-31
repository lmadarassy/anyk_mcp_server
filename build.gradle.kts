plugins {
    java
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/abevjava.jar"))
    implementation("io.modelcontextprotocol.sdk:mcp:1.1.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("hu.anyk.mcp.AnykMcpServer")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
