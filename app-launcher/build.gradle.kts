plugins {
    application
    id("org.openjfx.javafxplugin")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml", "javafx.graphics")
}

application {
    mainClass.set("com.todo.desktop.app.DesktopApp")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ui"))
    implementation(project(":sync"))

    implementation("com.google.firebase:firebase-admin:9.2.0")
    implementation("com.google.cloud:google-cloud-firestore:3.16.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.microsoft.graph:microsoft-graph:5.57.0")
    implementation("com.microsoft.azure:msal4j:1.15.0")
}
