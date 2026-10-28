package dev.pedalmate.sensor

/** The one sanctioned write to the bike. Returns true when the call was delivered, not when the motor arrived. */
fun interface BikeResistanceControl { fun setResistance(percent: Int): Boolean }
