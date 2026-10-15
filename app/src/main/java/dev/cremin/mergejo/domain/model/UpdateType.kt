package dev.cremin.mergejo.domain.model

/** The kind of dependency bump a Renovate PR represents, most significant first (order is relied on). */
enum class UpdateType {
    MAJOR,
    MINOR,
    PATCH,
    DIGEST,
    PIN,
    OTHER,
}
