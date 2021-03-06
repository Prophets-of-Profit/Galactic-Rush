package com.prophetsofprofit.galacticrush.logic

import com.prophetsofprofit.galacticrush.droneCost
import com.prophetsofprofit.galacticrush.kryo
import com.prophetsofprofit.galacticrush.logic.base.Facility
import com.prophetsofprofit.galacticrush.logic.change.Change
import com.prophetsofprofit.galacticrush.logic.change.PlayerChange
import com.prophetsofprofit.galacticrush.logic.drone.Drone
import com.prophetsofprofit.galacticrush.logic.drone.instruction.Instruction
import com.prophetsofprofit.galacticrush.logic.drone.instruction.InstructionType
import com.prophetsofprofit.galacticrush.logic.map.Galaxy
import com.prophetsofprofit.galacticrush.logic.map.Planet
import com.prophetsofprofit.galacticrush.startingMoney

/**
 * The main game object
 * Handles attributes of the current game, and is serialized for networking
 */
class Game(val initialPlayers: Array<Int>, val galaxy: Galaxy) {

    /**
     * Empty constructor for serialization
     */
    constructor() : this(arrayOf(), Galaxy(0, listOf()))

    //The amount of turns that have passed since the game was created
    var turnsPlayed = 0
    //The drones that currently exist in the game; should be ordered in order of creation
    val drones
        get() = this.galaxy.drones
    //The bases that currently exist in the game; ordered arbitrarily
    val bases
        get() = this.galaxy.bases
    //The players that are still in the game
    val players
        get() = this.bases.filter { it.facilityHealths.containsKey(Facility.HOME_BASE) }.map { it.ownerId }.toTypedArray()
    //The game's current phase
    var phase = GamePhase.DRAFT_PHASE
        set(value) {
            field = value
            this.hasBeenUpdated = true
        }
    //Whether the game has been updated; is used for determining whether to send the game over the network
    var hasBeenUpdated = false
    //The players who need to submit their changes for the drones to commence
    val waitingOn = this.players.toMutableList()
    //How much money each player has; maps id to money
    val money = this.players.map { it to startingMoney }.toMap().toMutableMap()
    //The list of things that happen after each drone turn
    var droneTurnChanges = mutableListOf<Change>()
    //Which instruction each player has; maps id to instructions
    val unlockedInstructions = this.players.map { it to mutableSetOf<Instruction>() }.toMap()
    //The instructions that can still be drafted
    val instructionPool = Instruction.values().map { instruction -> Array(instruction.occurrenceAmount) { instruction } }.toTypedArray().flatten().toMutableList()
    //The instructions that each player is being offered right now; initial value is draft for all players
    val currentDraft = players.map { it to this.drawInstructions().toMutableList() }.toMap()
    //The number of times the draft has been called in the current draft cycle
    var draftCounter = 0

    /**
     * Takes a random draw of (player number + 2) instructions from the instruction pool
     */
    fun drawInstructions(types: Array<InstructionType> = InstructionType.values()): List<Instruction> {
        val randomOptions = this.instructionPool.filter { it.types.intersect(types.asIterable()).isNotEmpty() }.shuffled()
        val draftOptions = randomOptions.slice(0 until minOf(randomOptions.size, this.players.size + 2))
        draftOptions.forEach { this.instructionPool.remove(it) }
        return draftOptions
    }

    /**
     * A method that collects changes, verifies their integrity, and then applies them to the game
     */
    fun collectChange(change: Change) {
        //Handle the draft
        if (this.phase == GamePhase.DRAFT_PHASE) {
            change as PlayerChange
            if (!this.waitingOn.contains(change.ownerId) || change.gainedInstructions.size != 1 || !this.currentDraft[change.ownerId]!!.containsAll(change.gainedInstructions)) {
                return
            }
            this.waitingOn.remove(change.ownerId)
            this.currentDraft[change.ownerId]!!.remove(change.gainedInstructions.first())
            this.unlockedInstructions[change.ownerId]!!.addAll(change.gainedInstructions)
            if (this.waitingOn.isEmpty()) {
                if (this.draftCounter < this.players.size - 1) {
                    draftCounter++
                    val temporaryOptionsToRotate = this.currentDraft[this.currentDraft.keys.last()]!!.toMutableList()
                    for (player in this.currentDraft.size - 1 downTo 1) {
                        this.currentDraft[this.currentDraft.keys.elementAt(player)]!!.clear()
                        this.currentDraft[this.currentDraft.keys.elementAt(player)]!!.addAll(this.currentDraft[this.currentDraft.keys.elementAt(player - 1)]!!)
                    }
                    this.currentDraft[this.currentDraft.keys.first()]!!.clear()
                    this.currentDraft[this.currentDraft.keys.first()]!!.addAll(temporaryOptionsToRotate)
                    this.hasBeenUpdated = true
                } else {
                    this.currentDraft.values.forEach { this.instructionPool.addAll(it); it.clear() }
                    this.draftCounter = 0
                    this.phase = GamePhase.PLAYER_FREE_PHASE
                }
                this.waitingOn.addAll(this.players)
            }
            //Handle the game phase
        } else if (this.phase == GamePhase.PLAYER_FREE_PHASE) {
            change as PlayerChange
            //Return if the player is not being waited on
            if (!this.waitingOn.contains(change.ownerId)) {
                return
            }
            //Calculate total change cost
            val changeCost = change.changedDrones.map { changedDrone ->
                //If the drone is new add it and all its instructions' costs
                if (!this.drones.any { it.id == changedDrone.id }) droneCost + changedDrone.instructions.sumBy { it.baseInstruction.cost }
                else changedDrone.instructions.minus(this.drones.first { it.id == changedDrone.id }.instructions).sumBy { it.baseInstruction.cost } - this.drones.first { it.id == changedDrone.id }.instructions.minus(changedDrone.instructions).sumBy { it.baseInstruction.cost }
            }.sum() + change.changedBases.map { changedBase ->
                if (!this.bases.any { it.locationId == changedBase.locationId && it.ownerId == changedBase.ownerId }) changedBase.facilityHealths.keys.sumBy { it.cost }
                else changedBase.facilityHealths.keys.minus(this.bases.first { it.locationId == changedBase.locationId && it.ownerId == changedBase.ownerId }.facilityHealths.keys).sumBy { it.cost } - this.bases.first { it.locationId == changedBase.locationId && it.ownerId == changedBase.ownerId }.facilityHealths.keys.minus(changedBase.facilityHealths.keys).sumBy { it.cost }
            }.sum()
            //Return if the change is invalid
            if (changeCost > this.money[change.ownerId]!! ||
                change.changedDrones.any { it.memoryAvailable < 0 }) {
                return
            }
            this.money[change.ownerId] = this.money[change.ownerId]!! - changeCost
            //Add all the changes into the game
            for (changedDrone in change.changedDrones) {
                this.drones.filter { it.id == changedDrone.id }.forEach { this.galaxy.getPlanetWithId(it.locationId)!!.drones.remove(it) }
                this.galaxy.getPlanetWithId(changedDrone.locationId)!!.drones.add(changedDrone)
            }
            for (changedPlanet in change.changedPlanets) {
                //TODO Only accounts for base changes right now; support other changes
                this.galaxy.getPlanetWithId(changedPlanet.id)!!.base = changedPlanet.base
            }
            for (changedBase in change.changedBases) {
                //TODO: costs for base changes aren't working
                this.galaxy.getPlanetWithId(changedBase.locationId)!!.base = changedBase
            }
            //TODO apply changes to instructions
            this.waitingOn.remove(change.ownerId)
            if (this.waitingOn.isEmpty()) {
                this.droneTurnChanges.clear()
                this.phase = GamePhase.DRONE_PHASE
            }
        }
    }

    /**
     * Performs one action per drone for all drones that can perform an action; won't be callable until game is ready
     * Returns whether the drone turns are done
     */
    fun doDroneTurn(): Boolean {
        //If phase isn't right, don't do anything
        if (this.phase != GamePhase.DRONE_PHASE) {
            return true
        }
        val changedDrones = mutableSetOf<Drone>()
        val changedPlanets = mutableSetOf<Planet>()
        //If this is the first doDroneTurn call for this turn, start the cycle for each drone
        if (this.droneTurnChanges.isEmpty()) {
            this.drones.forEach { it.startCycle(this) }
        }
        //Complete the actions of all the drones who can do actions in the queue
        this.drones.filterNot { it.queueFinished }.forEach {
            it.mainAction(this)
            changedDrones.add(it)
            changedPlanets.add(this.galaxy.getPlanetWithId(it.locationId)!!)
        }
        //Removes all of the destroyed drones
        this.drones.filter { it.isDestroyed }.forEach {
            this.galaxy.getPlanetWithId(it.locationId)!!.drones.remove(it)
            changedDrones.add(it)
        }
        //Remove all of the destroyed facilities and bases
        this.bases.filter { it.health <= 0 || it.facilityHealths.isEmpty() }.forEach {
            this.galaxy.getPlanetWithId(it.locationId)!!.base = null
            changedPlanets.add(this.galaxy.getPlanetWithId(it.locationId)!!)
        }
        //If all the drones are now finished, wait for players and reset drones
        val isDone = this.drones.all { it.queueFinished }
        if (isDone) {
            this.drones.forEach { it.endCycle(this) }
            this.drones.forEach { it.resetQueue(this) }
            this.turnsPlayed++
            this.currentDraft.values.forEach { it.addAll(this.drawInstructions()) }
            this.waitingOn.addAll(this.players)
            this.phase = GamePhase.DRAFT_PHASE
        }
        this.droneTurnChanges.add(Change().also { it.changedDrones.addAll(kryo.copy(changedDrones)); it.changedPlanets.addAll(kryo.copy(changedPlanets)) })
        return isDone
    }

}
