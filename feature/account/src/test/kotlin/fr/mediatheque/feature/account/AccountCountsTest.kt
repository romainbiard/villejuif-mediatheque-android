// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountCountsTest {
    private val now = Paris.instant(2026, 11, 17)

    @Test
    fun `counts from the loans and holds once read, so the counters agree with the lists`() {
        // The portal's counters were read earlier (one late), the second loan is late since then.
        val snapshot = AccountSnapshot(SampleData.loans, SampleData.holds, now)

        assertEquals(AccountCounts(loans = 3, late = 2, toPickUp = 1), AccountCounts.of(SampleData.patron, snapshot, now))
    }

    @Test
    fun `uses the portal's counters until the loans are read`() {
        assertEquals(AccountCounts(loans = 4, late = 1, toPickUp = 1), AccountCounts.of(SampleData.patron, null, now))
    }
}
