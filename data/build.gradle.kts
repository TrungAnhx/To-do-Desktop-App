plugins {
    `java-library`
}

dependencies {
    implementation(project(":domain"))

    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("com.google.cloud:google-cloud-firestore:3.16.0")
    implementation("com.google.guava:guava:33.2.0-jre")
    implementation("com.microsoft.graph:microsoft-graph:5.57.0")
    implementation("com.microsoft.azure:msal4j:1.15.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("org.slf4j:slf4j-api:2.0.13")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
