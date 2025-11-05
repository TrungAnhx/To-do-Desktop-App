plugins {
    `java-library`
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation("org.quartz-scheduler:quartz:2.3.2")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
