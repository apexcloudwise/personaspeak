// Criterion-1 consumption proof: this scratch Gradle root consumes the
// engine ARTIFACT by coordinates through a composite build — the same
// mechanism a host root uses (FlorisBoard: includeBuild("../engine") +
// implementation("com.personaspeak.engine:engine")). Not shipped; it
// exists so "consumable as an artifact" is a build receipt, not a claim.
rootProject.name = "personaspeak-engine-consumer-proof"

includeBuild("..")
include(":consumer")
