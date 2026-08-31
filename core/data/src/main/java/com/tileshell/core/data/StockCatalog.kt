package com.tileshell.core.data

/** One member of a [StockCategory]'s curated basket. */
data class StockSymbolRef(val symbol: String, val displayName: String)

/**
 * A curated sector basket a stock tile can follow as a whole (see
 * [StockTile.Selection.Category]). There's no free "stocks by sector"
 * enumeration API to draw these from live — same reasoning, and same
 * hand-curated/live-verified approach, as [CRICKET_TEAMS]/[IPL_TEAMS] — every
 * symbol here was checked against Yahoo Finance's chart endpoint directly.
 * [region] groups the picker into sections ("india" / "us"), same idea as
 * [SportsLeague.category]. [indexTicker]/[indexDisplayName] are the sector's
 * own index/tracking ETF (NIFTY BANK for banking, the XLF Financial Sector
 * SPDR for US financials, etc.) — a category tile's front face shows this
 * index's trend rather than any one member, since no single stock in the
 * basket represents "the sector" the way its own index does.
 */
data class StockCategory(
    val id: String,
    val displayName: String,
    val region: String,
    val indexTicker: String,
    val indexDisplayName: String,
    val symbols: List<StockSymbolRef>,
)

val STOCK_CATEGORIES: List<StockCategory> = listOf(
    StockCategory(
        "in-banking", "Banking", region = "india",
        indexTicker = "^NSEBANK", indexDisplayName = "NIFTY Bank",
        symbols = listOf(
            StockSymbolRef("HDFCBANK.NS", "HDFC Bank"),
            StockSymbolRef("ICICIBANK.NS", "ICICI Bank"),
            StockSymbolRef("SBIN.NS", "State Bank of India"),
            StockSymbolRef("KOTAKBANK.NS", "Kotak Mahindra Bank"),
            StockSymbolRef("AXISBANK.NS", "Axis Bank"),
        ),
    ),
    StockCategory(
        "in-it", "IT", region = "india",
        indexTicker = "^CNXIT", indexDisplayName = "NIFTY IT",
        symbols = listOf(
            StockSymbolRef("TCS.NS", "Tata Consultancy Services"),
            StockSymbolRef("INFY.NS", "Infosys"),
            StockSymbolRef("WIPRO.NS", "Wipro"),
            StockSymbolRef("HCLTECH.NS", "HCL Technologies"),
            StockSymbolRef("TECHM.NS", "Tech Mahindra"),
        ),
    ),
    StockCategory(
        "in-auto", "Auto", region = "india",
        indexTicker = "^CNXAUTO", indexDisplayName = "NIFTY Auto",
        symbols = listOf(
            StockSymbolRef("MARUTI.NS", "Maruti Suzuki"),
            // Tata Motors demerged in 2025 into commercial (TMCV) and
            // passenger (TMPV) entities — TMPV is the everyday-recognizable
            // "Tata Motors" car business, verified live under its new ticker.
            StockSymbolRef("TMPV.NS", "Tata Motors Passenger Vehicles"),
            StockSymbolRef("M&M.NS", "Mahindra & Mahindra"),
            StockSymbolRef("BAJAJ-AUTO.NS", "Bajaj Auto"),
            StockSymbolRef("HEROMOTOCO.NS", "Hero MotoCorp"),
        ),
    ),
    StockCategory(
        "in-pharma", "Pharma", region = "india",
        indexTicker = "^CNXPHARMA", indexDisplayName = "NIFTY Pharma",
        symbols = listOf(
            StockSymbolRef("SUNPHARMA.NS", "Sun Pharma"),
            StockSymbolRef("DRREDDY.NS", "Dr. Reddy's Labs"),
            StockSymbolRef("CIPLA.NS", "Cipla"),
            StockSymbolRef("DIVISLAB.NS", "Divi's Laboratories"),
            StockSymbolRef("APOLLOHOSP.NS", "Apollo Hospitals"),
        ),
    ),
    StockCategory(
        "in-energy", "Energy", region = "india",
        indexTicker = "^CNXENERGY", indexDisplayName = "NIFTY Energy",
        symbols = listOf(
            StockSymbolRef("RELIANCE.NS", "Reliance Industries"),
            StockSymbolRef("ONGC.NS", "ONGC"),
            StockSymbolRef("NTPC.NS", "NTPC"),
            StockSymbolRef("POWERGRID.NS", "Power Grid Corp"),
            StockSymbolRef("COALINDIA.NS", "Coal India"),
        ),
    ),
    StockCategory(
        "in-fmcg", "FMCG", region = "india",
        indexTicker = "^CNXFMCG", indexDisplayName = "NIFTY FMCG",
        symbols = listOf(
            StockSymbolRef("HINDUNILVR.NS", "Hindustan Unilever"),
            StockSymbolRef("ITC.NS", "ITC"),
            StockSymbolRef("NESTLEIND.NS", "Nestle India"),
            StockSymbolRef("BRITANNIA.NS", "Britannia Industries"),
            StockSymbolRef("TATACONSUM.NS", "Tata Consumer Products"),
        ),
    ),
    StockCategory(
        "us-financials", "Financials", region = "us",
        indexTicker = "XLF", indexDisplayName = "Financial Sector (XLF)",
        symbols = listOf(
            StockSymbolRef("JPM", "JPMorgan Chase"),
            StockSymbolRef("BAC", "Bank of America"),
            StockSymbolRef("WFC", "Wells Fargo"),
            StockSymbolRef("GS", "Goldman Sachs"),
            StockSymbolRef("MS", "Morgan Stanley"),
        ),
    ),
    StockCategory(
        "us-technology", "Technology", region = "us",
        indexTicker = "XLK", indexDisplayName = "Technology Sector (XLK)",
        symbols = listOf(
            StockSymbolRef("AAPL", "Apple"),
            StockSymbolRef("MSFT", "Microsoft"),
            StockSymbolRef("NVDA", "Nvidia"),
            StockSymbolRef("GOOGL", "Alphabet"),
            StockSymbolRef("AMZN", "Amazon"),
        ),
    ),
    StockCategory(
        "us-healthcare", "Healthcare", region = "us",
        indexTicker = "XLV", indexDisplayName = "Health Care Sector (XLV)",
        symbols = listOf(
            StockSymbolRef("UNH", "UnitedHealth Group"),
            StockSymbolRef("JNJ", "Johnson & Johnson"),
            StockSymbolRef("LLY", "Eli Lilly"),
            StockSymbolRef("PFE", "Pfizer"),
            StockSymbolRef("ABBV", "AbbVie"),
        ),
    ),
    StockCategory(
        "us-energy", "Energy", region = "us",
        indexTicker = "XLE", indexDisplayName = "Energy Sector (XLE)",
        symbols = listOf(
            StockSymbolRef("XOM", "Exxon Mobil"),
            StockSymbolRef("CVX", "Chevron"),
            StockSymbolRef("COP", "ConocoPhillips"),
            StockSymbolRef("SLB", "Schlumberger"),
            StockSymbolRef("EOG", "EOG Resources"),
        ),
    ),
)

/** Display order for [StockCategory.region] sections in the picker. */
val STOCK_CATEGORY_REGION_ORDER: List<String> = listOf("india", "us")

fun stockCategoryFor(id: String): StockCategory? = STOCK_CATEGORIES.find { it.id == id }
