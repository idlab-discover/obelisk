package idlab.obelisk.utils.test.rg

import java.util.Random
import kotlin.random.asKotlinRandom

object DeterministicRG {

    private val seed = java.lang.Long.parseLong(System.getProperty("randomSeed", "" + System.currentTimeMillis()))
    val instance = Random(seed)
    val ktInstance = instance.asKotlinRandom()

    init {
        println("Running test with seed: $seed (use -DrandomSeed property to override seed!)")
    }

}
