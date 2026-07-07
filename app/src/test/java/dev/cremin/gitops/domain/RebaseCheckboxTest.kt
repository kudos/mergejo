package dev.cremin.gitops.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RebaseCheckboxTest {

    private val unchecked =
        "Some description\n\n- [ ] <!-- rebase-check -->If you want to rebase/retry this PR, check this box 🔁\n\nfooter"

    @Test
    fun `requestRebase ticks the box`() {
        val result = RebaseCheckbox.requestRebase(unchecked)
        assertTrue(result != null)
        assertTrue(RebaseCheckbox.isChecked(result!!))
        assertTrue(result.contains("- [x] <!-- rebase-check -->"))
        // Only the checkbox changed.
        assertTrue(result.contains("If you want to rebase/retry this PR"))
    }

    @Test
    fun `already checked is a no-op`() {
        val checked = unchecked.replace("- [ ]", "- [x]")
        assertTrue(RebaseCheckbox.isChecked(checked))
        assertNull(RebaseCheckbox.requestRebase(checked))
    }

    @Test
    fun `no checkbox present`() {
        val body = "A PR body with no Renovate control checkbox."
        assertFalse(RebaseCheckbox.hasCheckbox(body))
        assertFalse(RebaseCheckbox.isChecked(body))
        assertNull(RebaseCheckbox.requestRebase(body))
    }
}
