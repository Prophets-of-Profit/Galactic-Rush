package com.prophetsofprofit.galacticrush.logic.drone

import com.prophetsofprofit.galacticrush.defaultDroneNames
import com.prophetsofprofit.galacticrush.logic.Game
import com.prophetsofprofit.galacticrush.logic.drone.instruction.Instruction
import com.prophetsofprofit.galacticrush.logic.drone.instruction.InstructionInstance
import com.prophetsofprofit.galacticrush.logic.map.Galaxy
import java.util.*

/**
 * Small data class that represents what can uniquely identify a drone
 * Drones can't be assigned integer IDs as usual because their creation is concurrent, and guaranteeing a unique int under those conditions is difficult
 */
data class DroneId(val ownerId: Int, val creationTime: Date) {

    /**
     * Empty constructor for serialization
     */
    constructor() : this(-1, Date(-1))
}

/**
 * A class that represent's a player's drone
 * Is the main unit of the game that carries out instructions
 * Is what is used to achieve victory
 * Location stores the id of the planet
 */
class Drone(val ownerId: Int, var locationId: Int) {

    //How much damage the drone deals
    var attack = 3
    //When the drone was initialized: the game assumes that this along with ownerId are unique
    val creationTime = Date()
    //The instructions the drone currently has
    val instructions = mutableListOf<InstructionInstance>()
    //How much memory the drone has
    val totalMemory = 10
    //A convenience getter to get how much free memory the drone has
    val memoryAvailable
        get() = this.totalMemory - instructions.sumBy { it.baseInstruction.memorySize }
    //Which instruction the drone is currently reading
    var pointer = 0
    //The potential ids of planets that the drone could select
    var selectablePlanetIds: MutableList<Int>? = null
    //The potential ids of drones that the drone could select
    var selectableDroneIds: MutableList<DroneId>? = null
    //Whether the drone is done completing its command queue
    var queueFinished = false
    //Whether the drone is destroyed or not
    var isDestroyed = false
    //What the drone displays as in lists
    var name = defaultDroneNames.toMutableList().shuffled().first()
    //What uniquely identifies the drone
    val id: DroneId
        get() = DroneId(this.ownerId, this.creationTime)
    //Data that the drone has that may persist between turns
    val persistentData = mutableMapOf<String, String>()

    /**
     * Empty constructor for serialization
     */
    constructor() : this(-1, -1)

    /**
     * Adds the given instruction this drone at the specified location
     */
    fun addInstruction(instruction: Instruction, locationIndex: Int = this.instructions.size) {
        this.instructions.add(locationIndex, InstructionInstance(instruction))
    }

    /**
     * Removes the first instance of the given instruction
     */
    fun removeInstruction(instruction: InstructionInstance, game: Game) {
        instruction.baseInstruction.removeAction(this, game, instruction)
        this.instructions.remove(instruction)
    }

    /**
     * Removes the instruction at the given index
     */
    fun removeInstruction(locationIndex: Int, game: Game) {
        this.instructions[locationIndex].baseInstruction.removeAction(this, game, this.instructions[locationIndex])
        this.instructions.removeAt(locationIndex)
    }

    /**
     * Calls startCycle for all instructions in the queue
     */
    fun startCycle(game: Game) {
        this.resetSelectables(game)
        this.instructions.forEach { it.baseInstruction.startCycleAction(this, game, it) }
    }

    /**
     * Calls mainAction for the drone's current instruction and then increments its pointer
     */
    fun mainAction(game: Game) {
        if (this.instructions.isEmpty()) {
            this.queueFinished = true
            return
        }
        this.instructions[this.pointer].baseInstruction.mainAction(this, game, this.instructions[this.pointer])
        this.advancePointer(1)
    }

    /**
     * Calls endCycle for all instructions in the queue
     */
    fun endCycle(game: Game) {
        this.instructions.forEach { it.baseInstruction.endCycleAction(this, game, it) }
    }

    /**
     * Advances the pointer by some number of steps, changing what the drone will read next
     */
    fun advancePointer(steps: Int) {
        this.pointer += steps
        if (this.pointer >= this.instructions.size) {
            this.pointer = this.instructions.lastIndex
            this.queueFinished = true
        }
    }

    /**
     * Resets the drone's pointer for the next turn
     */
    fun resetQueue(game: Game) {
        this.pointer = 0
        this.queueFinished = false
        this.resetSelectables(game)
    }

    /**
     * Attempts to distribute damage among instructions
     */
    fun takeDamage(damage: Int, game: Game) {
        val damageToAll = damage / this.instructions.size
        val numToReceieveExtra = damage % this.instructions.size
        this.instructions.forEach { it.health -= damageToAll }
        this.instructions.subList(0, numToReceieveExtra).forEach { it.health-- }
        this.instructions.filter { it.health <= 0 }.forEach { this.removeInstruction(it, game) }
        this.isDestroyed = this.instructions.isEmpty()
    }

    /**
     * Moves the drone to the given planet
     */
    fun moveToPlanet(id: Int, game: Game) {
        game.galaxy.getPlanetWithId(this.locationId)!!.drones.remove(this)
        this.locationId = id
        game.galaxy.getPlanetWithId(this.locationId)!!.drones.add(this)
        this.resetSelectables(game)
    }

    /**
     * Resets the possible selectable planets and drones
     */
    fun resetSelectables(game: Game) {
        this.selectableDroneIds = game.galaxy.getPlanetWithId(this.locationId)!!.drones.map { it.id }.toMutableList()
        this.selectablePlanetIds = game.galaxy.planetsAdjacentTo(this.locationId).toMutableList()
    }

    /**
     * How the drone will be displayed on the planet listing
     */
    override fun toString(): String {
        return "$name ($ownerId)"
    }

}