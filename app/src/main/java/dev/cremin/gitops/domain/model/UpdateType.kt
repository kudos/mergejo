package dev.cremin.gitops.domain.model

/** The kind of dependency bump a Renovate PR represents. */
enum class UpdateType {
    MAJOR,
    MINOR,
    PATCH,
    DIGEST,
    PIN,
    OTHER,
}
