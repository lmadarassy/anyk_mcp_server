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

// --- ANYK telepitesi konyvtar feloldasa ---
// Sorrend: -PanykHome=... > ANYK_HOME kornyezeti valtozo > gradle.properties (anykHome)
val anykHome: String = (findProperty("anykHome") as String?)
    ?: System.getenv("ANYK_HOME")
    ?: ""

val abevJar = file("$anykHome/abevjava.jar")

if (anykHome.isBlank() || !abevJar.exists()) {
    logger.warn(
        "\n============================================================\n" +
        "  FIGYELEM: az abevjava.jar nem talalhato!\n" +
        "  Add meg az ANYK telepitesi konyvtarat az alabbiak egyikevel:\n" +
        "    ./gradlew build -PanykHome=/eleresi/ut/az/abevjava\n" +
        "    ANYK_HOME=/eleresi/ut/az/abevjava ./gradlew build\n" +
        "    vagy a gradle.properties-ben: anykHome=/eleresi/ut/az/abevjava\n" +
        "  (A konyvtar tartalmazza az abevjava.jar-t es az eroforrasok/-t.)\n" +
        "  Keresett hely: ${abevJar.absolutePath}\n" +
        "============================================================"
    )
}

dependencies {
    // abevjava.jar az ANYK telepitesbol - compileOnly, NEM csomagoljuk be
    compileOnly(files(abevJar))

    implementation("io.modelcontextprotocol.sdk:mcp:1.1.4")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("hu.anyk.mcp.AnykMcpServer")
    // Az ANYK home atadasa a futtatashoz (a szerver innen olvassa a prop.sys.root-ot)
    if (anykHome.isNotBlank()) {
        applicationDefaultJvmArgs = listOf("-Danyk.home=$anykHome")
    }
}

// A `gradle run` classpath-jara felvesszuk az abevjava.jar-t
tasks.named<JavaExec>("run") {
    if (abevJar.exists()) {
        classpath += files(abevJar)
    }
    // ANYK home atadasa argumentumkent is (ha nincs env/property)
    if (anykHome.isNotBlank()) {
        args = listOf(anykHome)
    }
}

// Teszt futtatashoz is kell az abevjava.jar
tasks.withType<Test> {
    if (abevJar.exists()) {
        classpath += files(abevJar)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
