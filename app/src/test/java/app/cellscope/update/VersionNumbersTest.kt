package app.cellscope.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionNumbersTest {
    @Test
    fun detectsNewerNumericVersions() {
        assertTrue(VersionNumbers.isNewer("0.9.0", "0.10.0"))
        assertTrue(VersionNumbers.isNewer("1.2", "1.2.1"))
        assertFalse(VersionNumbers.isNewer("1.2.0", "1.2"))
        assertFalse(VersionNumbers.isNewer("2.0.0", "1.99.99"))
    }

    @Test
    fun rejectsNonNumericReleaseTags() {
        assertFalse(VersionNumbers.isNewer("1.0.0", "nightly"))
        assertFalse(VersionNumbers.isNewer("1.0.0", "1.1.0-beta"))
    }
}
