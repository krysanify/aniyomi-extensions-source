plugins {
    id("lib-multisrc")
}

baseVersionCode = 4

dependencies {
    api(project(":lib:megacloud-extractor"))
    api(project(":lib:streamtape-extractor"))
    api(project(":lib:vidsrc-extractor"))
}
