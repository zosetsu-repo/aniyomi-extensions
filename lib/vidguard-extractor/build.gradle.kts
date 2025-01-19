plugins {
    id("lib-android")
}

dependencies {
    implementation(project(":lib:playlist-utils"))
    implementation("org.mozilla:rhino:1.8.0")
}
