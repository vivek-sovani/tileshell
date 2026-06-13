package com.tileshell.core.data.seed

import com.tileshell.core.data.TileSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutSeederTest {

    private val seeder = LayoutSeeder()

    /** Resolver that maps the given role ids to fabricated components. */
    private fun resolverFor(vararg appIds: String): RoleResolver {
        val queries = appIds.associate { id ->
            DefaultLayout.roleFor(id)!! to ResolvedComponent("com.$id", ".Main", id)
        }
        return RoleResolver { queries[it] }
    }

    @Test
    fun `only resolvable app tiles are seeded, in order, with contiguous positions`() {
        val seeded = seeder.seed(resolver = resolverFor("clock", "phone", "settings"))

        val apps = seeded.filterIsInstance<SeededTile.App>()
        assertEquals(listOf("t-clock", "t-phone", "t-settings"), apps.map { it.id })
        assertEquals(listOf(0, 1, 2), seeded.map { it.position })
    }

    @Test
    fun `roleless tiles (weather, notes, bank) are skipped`() {
        // Resolve everything that HAS a role; weather/notes/bank have none.
        val withRole = DefaultLayout.DEFAULT_TILES
            .flatMap { if (it.isGroup) it.children else listOfNotNull(it.app) }
            .distinct()
            .filter { DefaultLayout.roleFor(it) != null }
        val seeded = seeder.seed(resolver = resolverFor(*withRole.toTypedArray()))

        val ids = seeded.map { it.id }
        assertTrue("weather skipped", "t-weather" !in ids)
        assertTrue("notes skipped", "t-notes" !in ids)
        assertTrue("bank skipped", "t-bank" !in ids)
        assertTrue("clock kept", "t-clock" in ids)
    }

    @Test
    fun `app whose role has no installed match is skipped`() {
        // phone resolves; clock's role yields nothing from this resolver.
        val seeded = seeder.seed(resolver = resolverFor("phone"))
        assertEquals(listOf("t-phone"), seeded.map { it.id })
    }

    @Test
    fun `folder keeps only resolvable children`() {
        // social children: contacts, mail, messages, people. Resolve mail and
        // messages (distinct roles); contacts/people (shared APP_CONTACTS role)
        // stay unresolved here. Children keep declared order.
        val seeded = seeder.seed(resolver = resolverFor("mail", "messages"))
        val folder = seeded.filterIsInstance<SeededTile.Folder>().single()
        assertEquals("social", folder.name)
        assertEquals(listOf("com.mail", "com.messages"), folder.children.map { it.packageName })
    }

    @Test
    fun `folder with no resolvable children is dropped`() {
        // Resolve only clock — none of the social children resolve.
        val seeded = seeder.seed(resolver = resolverFor("clock"))
        assertTrue(seeded.none { it is SeededTile.Folder })
        assertTrue("g-social" !in seeded.map { it.id })
    }

    @Test
    fun `folder children are de-duplicated by component`() {
        // Map every social child role to the SAME component.
        val socialRoles = listOf("contacts", "mail", "messages", "people")
            .mapNotNull(DefaultLayout::roleFor)
        val shared = ResolvedComponent("com.shared", ".Main", "shared")
        val resolver = RoleResolver { if (it in socialRoles) shared else null }

        val folder = seeder.seed(resolver = resolver)
            .filterIsInstance<SeededTile.Folder>().single()
        assertEquals(1, folder.children.size)
    }

    @Test
    fun `seeded tiles preserve declared size and color`() {
        val seeded = seeder.seed(resolver = resolverFor("clock", "calc"))
        val clock = seeded.first { it.id == "t-clock" }
        val calc = seeded.first { it.id == "t-calc" }
        assertEquals(TileSize.WIDE, clock.size)
        assertEquals("cobalt", clock.colorId)
        assertEquals(TileSize.SMALL, calc.size)
        assertEquals("steel", calc.colorId)
    }

    @Test
    fun `role table maps known roles and rejects unknown ones`() {
        assertEquals(RoleQuery.Action("android.intent.action.SHOW_ALARMS"), DefaultLayout.roleFor("clock"))
        assertEquals(RoleQuery.DefaultSms, DefaultLayout.roleFor("messages"))
        assertEquals(RoleQuery.Category("android.intent.category.APP_CALCULATOR"), DefaultLayout.roleFor("calc"))
        assertEquals(null, DefaultLayout.roleFor("weather"))
        assertEquals(null, DefaultLayout.roleFor("bank"))
    }
}
