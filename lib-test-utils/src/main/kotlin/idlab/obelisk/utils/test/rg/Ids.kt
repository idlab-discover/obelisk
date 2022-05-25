package idlab.obelisk.utils.test.rg

object Ids {

    private val SEPARATORS = " -_/:#@$;"

    private val BASE_METRIC_IDS = arrayOf("  http requests  code ", "airquality no2", "temperature celsius", "urn spat count", "1800Mhz averagePower", "acceleration", "calculated radius orientation", "RSSI LQI NdTxErr", "metrics humidity rh", " button pressed")

    fun metricId(): String {
        val tmp = BASE_METRIC_IDS[DeterministicRG.instance.nextInt(BASE_METRIC_IDS.size)]
                .replace(" ", "" + SEPARATORS[DeterministicRG.instance.nextInt(SEPARATORS.length)])
        return if (tmp.startsWith("#")) "z$tmp" else tmp //Apparently InfluxDB cannot support measurements starting with #, quick workaround for generating valid metric ids.
    }

    fun thingId(): String {
        return Values.stringBetween(6, 64)
    }

}
