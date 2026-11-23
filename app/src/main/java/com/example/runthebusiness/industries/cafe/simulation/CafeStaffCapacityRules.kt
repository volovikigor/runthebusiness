package com.example.runthebusiness.industries.cafe.simulation

/** Approximate shared service workload, in worker-minutes per 15-minute slot.
 * The MVP does not model individual shifts or queues across slots.
 */
object CafeStaffCapacityRules {
    fun slotCapacity(activeProductivity: List<Double>): Double =
        activeProductivity.sumOf { it.coerceAtLeast(0.0) } * 15.0

    fun workloadMinutes(preparationMinutes: Int): Double =
        preparationMinutes.coerceAtLeast(1).toDouble()

    fun canServe(usedMinutes: Double, additionalMinutes: Double, capacityMinutes: Double): Boolean =
        usedMinutes + additionalMinutes <= capacityMinutes + 1e-8
}
