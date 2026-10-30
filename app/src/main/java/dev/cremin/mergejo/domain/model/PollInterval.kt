package dev.cremin.mergejo.domain.model

/**
 * How often the background check for new Renovate PRs runs. [OFF] disables it.
 * WorkManager's minimum periodic interval is 15 minutes, so nothing shorter is offered.
 */
enum class PollInterval(val minutes: Long) {
    OFF(0),
    MINUTES_15(15),
    MINUTES_30(30),
    HOURS_1(60),
    HOURS_3(3 * 60),
    HOURS_6(6 * 60),
    HOURS_12(12 * 60),
    HOURS_24(24 * 60),
}
