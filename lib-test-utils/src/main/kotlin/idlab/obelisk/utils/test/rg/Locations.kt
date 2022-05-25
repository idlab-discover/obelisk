package idlab.obelisk.utils.test.rg

object Locations {

    fun latitude(): Double {
        return DeterministicRG.instance.nextDouble() * 180 - 90
    }

    fun longitude(): Double {
        return DeterministicRG.instance.nextDouble() * 360 - 180
    }

}
