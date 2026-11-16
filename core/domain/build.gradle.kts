// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.jvm.library)
}

// Business rules without Android: Code 39, reminders, notification center, returns summary, school holidays.
dependencies {
    api(projects.core.model)
}
