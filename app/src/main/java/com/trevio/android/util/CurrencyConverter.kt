package com.trevio.android.util

import androidx.annotation.StringRes
import com.trevio.android.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.round

object CurrencyConverter {

    private val currencyLocales = mapOf(
        "INR" to Locale("en", "IN"),
        "USD" to Locale("en", "US"),
        "EUR" to Locale("en", "IE"),
        "GBP" to Locale("en", "GB"),
        "JPY" to Locale("ja", "JP"),
        "AUD" to Locale("en", "AU"),
        "CAD" to Locale("en", "CA"),
        "SGD" to Locale("en", "SG"),
        "AED" to Locale("ar", "AE"),
        "SAR" to Locale("ar", "SA"),
        "PKR" to Locale("ur", "PK"),
        "BDT" to Locale("bn", "BD"),
        "LKR" to Locale("en", "LK"),
        "NPR" to Locale("ne", "NP"),
        "ZAR" to Locale("en", "ZA"),
        "NGN" to Locale("en", "NG"),
        "KES" to Locale("sw", "KE"),
        "CHF" to Locale("de", "CH"),
        "SEK" to Locale("sv", "SE"),
        "NOK" to Locale("nb", "NO"),
        "DKK" to Locale("da", "DK"),
        "PLN" to Locale("pl", "PL"),
        "CZK" to Locale("cs", "CZ"),
        "HUF" to Locale("hu", "HU"),
        "RON" to Locale("ro", "RO"),
        "BGN" to Locale("bg", "BG"),
        "ISK" to Locale("is", "IS"),
        "RUB" to Locale("ru", "RU"),
        "UAH" to Locale("uk", "UA"),
        "CNY" to Locale("zh", "CN"),
        "HKD" to Locale("zh", "HK"),
        "TWD" to Locale("zh", "TW"),
        "KRW" to Locale("ko", "KR"),
        "THB" to Locale("th", "TH"),
        "VND" to Locale("vi", "VN"),
        "MYR" to Locale("ms", "MY"),
        "IDR" to Locale("id", "ID"),
        "PHP" to Locale("en", "PH"),
        "TRY" to Locale("tr", "TR"),
        "BRL" to Locale("pt", "BR"),
        "ARS" to Locale("es", "AR"),
        "MXN" to Locale("es", "MX"),
        "COP" to Locale("es", "CO"),
        "CLP" to Locale("es", "CL"),
        "PEN" to Locale("es", "PE"),
        "NZD" to Locale("en", "NZ"),
        "ILS" to Locale("he", "IL"),
        "EGP" to Locale("ar", "EG"),
        "GHS" to Locale("ak", "GH"),
        "KZT" to Locale("kk", "KZ"),
        "MNT" to Locale("mn", "MN"),
        "GEL" to Locale("ka", "GE"),
        "AMD" to Locale("hy", "AM"),
        "AZN" to Locale("az", "AZ")
    )

    fun getLocaleForCurrency(currency: String): Locale {
        return currencyLocales[currency] ?: Locale("en", "US")
    }

    fun formatDate(timestamp: Long, currency: String, includeTime: Boolean = false, timezone: String? = null): String {
        if (timestamp <= 0L) return ""
        val locale = getLocaleForCurrency(currency)
        val pattern = if (includeTime) "dd MMM yyyy, HH:mm" else "dd MMM yyyy"
        val formatter = SimpleDateFormat(pattern, locale)
        if (!timezone.isNullOrEmpty()) {
            formatter.timeZone = TimeZone.getTimeZone(timezone)
        }
        return formatter.format(Date(timestamp))
    }

    fun convertFromBase(
        amountInBase: Double,
        toCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (toCurrency == AppConstants.BASE_CURRENCY) return amountInBase
        val rate = rates[toCurrency] ?: return amountInBase
        return round(amountInBase * rate * 100) / 100
    }

    fun convertToBase(
        amount: Double,
        fromCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (fromCurrency == AppConstants.BASE_CURRENCY) return amount
        val rate = rates[fromCurrency] ?: return amount
        return round(amount / rate * 100) / 100
    }

    fun getRateToBase(
        currency: String,
        rates: Map<String, Double>
    ): Double {
        if (currency == AppConstants.BASE_CURRENCY) return 1.0
        val rate = rates[currency] ?: return 1.0
        return 1.0 / rate
    }

    private val currencySymbols = mapOf(
        "INR" to "₹",
        "USD" to "$",
        "EUR" to "€",
        "GBP" to "£",
        "JPY" to "¥",
        "AUD" to "A$",
        "CAD" to "C$",
        "SGD" to "S$",
        "AED" to "د.إ",
        "SAR" to "﷼",
        "PKR" to "₨",
        "BDT" to "৳",
        "LKR" to "₨",
        "NPR" to "₨",
        "ZAR" to "R",
        "NGN" to "₦",
        "KES" to "KSh",
        "CHF" to "CHF",
        "SEK" to "kr",
        "NOK" to "kr",
        "DKK" to "kr",
        "PLN" to "zł",
        "CZK" to "Kč",
        "HUF" to "Ft",
        "RON" to "lei",
        "BGN" to "lev",
        "ISK" to "kr",
        "RUB" to "₽",
        "UAH" to "₴",
        "CNY" to "¥",
        "HKD" to "HK$",
        "TWD" to "NT$",
        "KRW" to "₩",
        "THB" to "฿",
        "VND" to "₫",
        "MYR" to "RM",
        "IDR" to "Rp",
        "PHP" to "₱",
        "TRY" to "₺",
        "BRL" to "R$",
        "ARS" to "$",
        "MXN" to "$",
        "COP" to "$",
        "CLP" to "$",
        "PEN" to "S/",
        "NZD" to "NZ$",
        "ILS" to "₪",
        "EGP" to "E£",
        "GHS" to "₵",
        "KZT" to "₸",
        "MNT" to "₮",
        "GEL" to "₾",
        "AMD" to "֏",
        "AZN" to "₼"
    )

    /** Currency code + symbol + display name resource, derived from the symbols map */
    data class CurrencyInfo(val code: String, val symbol: String, @StringRes val nameResId: Int)

    private val currencyNameResIds = mapOf(
        "INR" to R.string.currency_inr,
        "USD" to R.string.currency_usd,
        "EUR" to R.string.currency_eur,
        "GBP" to R.string.currency_gbp,
        "JPY" to R.string.currency_jpy,
        "AUD" to R.string.currency_aud,
        "CAD" to R.string.currency_cad,
        "SGD" to R.string.currency_sgd,
        "AED" to R.string.currency_aed,
        "SAR" to R.string.currency_sar,
        "PKR" to R.string.currency_pkr,
        "BDT" to R.string.currency_bdt,
        "LKR" to R.string.currency_lkr,
        "NPR" to R.string.currency_npr,
        "ZAR" to R.string.currency_zar,
        "NGN" to R.string.currency_ngn,
        "KES" to R.string.currency_kes,
        "CHF" to R.string.currency_chf,
        "SEK" to R.string.currency_sek,
        "NOK" to R.string.currency_nok,
        "DKK" to R.string.currency_dkk,
        "PLN" to R.string.currency_pln,
        "CZK" to R.string.currency_czk,
        "HUF" to R.string.currency_huf,
        "RON" to R.string.currency_ron,
        "BGN" to R.string.currency_bgn,
        "ISK" to R.string.currency_isk,
        "RUB" to R.string.currency_rub,
        "UAH" to R.string.currency_uah,
        "CNY" to R.string.currency_cny,
        "HKD" to R.string.currency_hkd,
        "TWD" to R.string.currency_twd,
        "KRW" to R.string.currency_krw,
        "THB" to R.string.currency_thb,
        "VND" to R.string.currency_vnd,
        "MYR" to R.string.currency_myr,
        "IDR" to R.string.currency_idr,
        "PHP" to R.string.currency_php,
        "TRY" to R.string.currency_try,
        "BRL" to R.string.currency_brl,
        "ARS" to R.string.currency_ars,
        "MXN" to R.string.currency_mxn,
        "COP" to R.string.currency_cop,
        "CLP" to R.string.currency_clp,
        "PEN" to R.string.currency_pen,
        "NZD" to R.string.currency_nzd,
        "ILS" to R.string.currency_ils,
        "EGP" to R.string.currency_egp,
        "GHS" to R.string.currency_ghs,
        "KZT" to R.string.currency_kzt,
        "MNT" to R.string.currency_mnt,
        "GEL" to R.string.currency_gel,
        "AMD" to R.string.currency_amd,
        "AZN" to R.string.currency_azn
    )

    /** Returns the string resource ID for a currency code's display name. */
    @StringRes
    fun currencyNameResId(code: String): Int = currencyNameResIds[code] ?: R.string.currency_usd

    /** List of all supported currencies for dropdowns */
    val SUPPORTED_CURRENCIES: List<CurrencyInfo> = currencySymbols.keys.map { code ->
        CurrencyInfo(code, currencySymbols[code] ?: code, currencyNameResId(code))
    }

    /** Returns a generic source-to-target rate using rates whose base is [AppConstants.BASE_CURRENCY]. */
    fun getRate(sourceCurrency: String, targetCurrency: String, rates: Map<String, Double>): Double {
        if (sourceCurrency == targetCurrency) return 1.0
        val sourceRate = if (sourceCurrency == AppConstants.BASE_CURRENCY) 1.0 else rates[sourceCurrency] ?: return 1.0
        val targetRate = if (targetCurrency == AppConstants.BASE_CURRENCY) 1.0 else rates[targetCurrency] ?: return 1.0
        return targetRate / sourceRate
    }

    fun convertCurrency(amount: Double, fromCurrency: String, toCurrency: String, rates: Map<String, Double>): Double {
        if (fromCurrency == toCurrency) return amount
        return Math.round(amount * getRate(fromCurrency, toCurrency, rates) * 100) / 100.0
    }

    fun getCurrencySymbol(currency: String): String {
        return currencySymbols[currency] ?: currency
    }

    fun formatCurrency(amount: Double, currency: String): String {
        val symbol = currencySymbols[currency] ?: ""
        val formatted = String.format(Locale.getDefault(), "%,.2f", amount)
        return "$symbol$formatted"
    }

    fun formatConverted(
        amount: Double,
        fromCurrency: String,
        rates: Map<String, Double>?,
        userCurrency: String
    ): String {
        if (rates == null || fromCurrency == userCurrency) {
            return formatCurrency(amount, userCurrency)
        }
        val baseAmount = convertToBase(amount, fromCurrency, rates)
        val converted = convertFromBase(baseAmount, userCurrency, rates)
        return formatCurrency(converted, userCurrency)
    }
}
