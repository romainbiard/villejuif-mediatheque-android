// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

plugins {
    alias(libs.plugins.mediatheque.jvm.library)
}

// Règles métier sans Android : Code 39, rappels, centre de notifications, résumé des retours, vacances scolaires.
dependencies {
    api(projects.core.model)
}
