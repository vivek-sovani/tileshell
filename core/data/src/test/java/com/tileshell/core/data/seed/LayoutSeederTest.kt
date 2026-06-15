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
    fun `resolvable and live-only app tiles are seeded, in order, with contiguous positions`() {
        val seeded = seeder.seed(resolver = resolverFor("clock", "phone", "settings"))

        // clock/phone/settings resolve; weather/calendar seed as liveOnly; the rest
        // (camera, people, mail, …) drop out. Declared order is preserved.
        val apps = seeded.filterIsInstance<SeededTile.App>()
        assertEquals(
            listOf("t-clock", "t-phone", "t-weather", "t-cal", "t-settings"),
            apps.map { it.id },
        )
        assertEquals(listOf(0, 1, 2, 3, 4), seeded.map { it.position })
    }

    @Test
    fun `roleless non-live tiles (notes, bank) are skipped`() {
        // Resolve everything that HAS a role; notes/bank have none and are not
        // liveOnly, so they drop out.
        val withRole = DefaultLayout.DEFAULT_TILES
            .flatMap { if (it.isGroup) it.children else listOfNotNull(it.app) }
            .distinct()
            .filter { DefaultLayout.roleFor(it) != null }
        val seeded = seeder.seed(resolver = resolverFor(*withRole.toTypedArray()))

        val ids = seeded.map { it.id }
        assertTrue("notes skipped", "t-notes" !in ids)
        assertTrue("bank skipped", "t-bank" !in ids)
        assertTrue("clock kept", "t-clock" in ids)
    }

    @Test
    fun `self-contained live tiles seed even when no app resolves`() {
        // Nothing resolves. clock, weather and calendar are liveOnly, so all three
        // seed with a blank, inert launch target and keep their live glyph key.
        val seeded = seeder.seed(resolver = RoleResolver { null })
        val apps = seeded.filterIsInstance<SeededTile.App>().associateBy { it.id }

        assertTrue("clock seeded", "t-clock" in apps.keys)
        assertTrue("weather seeded", "t-weather" in apps.keys)
        assertTrue("calendar seeded", "t-cal" in apps.keys)
        assertEquals("", apps.getValue("t-clock").component.packageName)
        assertEquals("clock", apps.getValue("t-clock").iconKey)
        assertEquals("weather", apps.getValue("t-weather").iconKey)
        assertEquals("calendar", apps.getValue("t-cal").iconKey)
    }

    @Test
    fun `live tile uses its resolved app when one exists`() {
        // calendar resolves here, so it keeps a real launch target.
        val seeded = seeder.seed(resolver = resolverFor("calendar"))
        val cal = seeded.filterIsInstance<SeededTile.App>().single { it.id == "t-cal" }
        assertEquals("com.calendar", cal.component.packageName)
    }

    @Test
    fun `non-live app whose role has no installed match is skipped`() {
        // phone resolves; camera's role yields nothing and camera is not liveOnly,
        // so it drops out. The liveOnly clock/weather/calendar tiles still seed.
        val seeded = seeder.seed(resolver = resolverFor("phone"))
        val ids = seeded.map { it.id }
        assertTrue("phone kept", "t-phone" in ids)
        assertTrue("camera skipped (role unresolved, not liveOnly)", "t-camera" !in ids)
        assertTrue("clock seeded (liveOnly)", "t-clock" in ids)
    }

    @Test
    fun `folder keeps only resolvable children`() {
        // social children: contacts, mail, messages, people. Resolve mail and
        // messages (distinct roles); contacts/people (shared APP_CONTACTS role)
        // stay unresolved here. Children keep declared order.
        val seeded = seeder.seed(resolver = resolverFor("mail", "messages"))
        val folder = seeded.filterIsInstance<SeededTile.Folder>().single()
        assertEquals("social", folder.name)
        assertEquals(listOf("com.mail", "com.messages"), folder.children.map { it.component.packageName })
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
    fun `app tiles carry the monoline glyph key, remapping browser and notes`() {
        val seeded = seeder.seed(resolver = resolverFor("clock", "browser"))
        val apps = seeded.filterIsInstance<SeededTile.App>().associateBy { it.id }
        assertEquals("clock", apps.getValue("t-clock").iconKey)
        assertEquals("web", apps.getValue("t-browser").iconKey) // browser → web glyph
    }

    @Test
    fun `folder children carry glyph keys`() {
        val folder = seeder.seed(resolver = resolverFor("mail", "messages"))
            .filterIsInstance<SeededTile.Folder>().single()
        assertEquals(listOf("mail", "messages"), folder.children.map { it.iconKey })
    }

    @Test
    fun `role table maps known roles and rejects unknown ones`() {
        assertEquals(
            RoleQuery.AnyOf(
                listOf(
                    RoleQuery.Action("android.intent.action.SHOW_ALARMS"),
                    RoleQuery.Action("android.intent.action.SET_ALARM"),
                    RoleQuery.Action("android.intent.action.SHOW_TIMERS"),
                ),
            ),
            DefaultLayout.roleFor("clock"),
        )
        assertEquals(RoleQuery.DefaultSms, DefaultLayout.roleFor("messages"))
        assertEquals(RoleQuery.Category("android.intent.category.APP_CALCULATOR"), DefaultLayout.roleFor("calc"))
        assertEquals(null, DefaultLayout.roleFor("weather"))
        assertEquals(null, DefaultLayout.roleFor("bank"))
    }
}
