package io.github.leonfox28.maxbo.ui.debug

import io.github.leonfox28.maxbo.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteForTest {
    @Test
    fun `opens the pages the Flutter router had paths for`() {
        assertEquals(Route.ProfileByName("某人"), routeFor("/n/某人"))
        assertEquals(Route.Profile("1001"), routeFor("/u/1001"))
        assertEquals(Route.StatusDetail("42"), routeFor("/status/42"))
        assertEquals(Route.SearchResults("#话题#"), routeFor("/results?q=%23话题%23"))
    }

    @Test
    fun `anything else opens nothing`() {
        assertNull(routeFor("/n/"))
        assertNull(routeFor("/u/1/2"))
        assertNull(routeFor("/results?q="))
        assertNull(routeFor("/settings"))
        assertNull(routeFor("n/name"))
    }
}
