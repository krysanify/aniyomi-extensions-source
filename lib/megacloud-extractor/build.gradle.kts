plugins {
    id("lib-android")
}

dependencies {
    implementation(project(":lib:cryptoaes"))
    implementation(project(":lib:playlist-utils"))
    implementation(libs.rhino)
}
