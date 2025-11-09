plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "ProjekatKomponente1"
include("spec")

include("txtImpl")
include("test")
include("testApp")
include("calcc")
include("htmplPdfImpl")
include("markdownImpl")