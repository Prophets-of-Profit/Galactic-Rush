package com.prophetsofprofit.galacticrush.logic.map

import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector2
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A class that is basically the map that the game is played on
 * Contains a bunch of planets which are essentially the game 'tiles'
 * Planets are connected as a graph rather than sequentially
 */
class Galaxy(numPlanets: Int) {

    //The planets that are in the galaxy: serve as 'tiles' of the game, but are connected as a graph
    val planets = mutableListOf<Planet>()
    //The cosmic highways that are in the galaxy: serve as the 'paths' or 'connections' of the game
    val highways = mutableListOf<CosmicHighway>()

    /**
     * Galaxy constructor generates all the planets and terrain and values and such
     * The algorithm attempts to maximize the spread of the planets probabilistically:
     *  We start with equal probability for any setting in space
     *  For a certain number of cycles, a planet is generated based on the probability of each tile being chosen
     *  Then we can update the probabilities of the tiles to make those close to the new planet less favorable
     *  Probabilities are relative, so we can just add some scalar to each tile's probability based on its distance from
     *  the planet every step
     * While this isn't a world, a good term for this would be 'worldgen'
     */
    init {
        /*
        * The planets are generated by choosing from a finite set of locations arranged in a square grid
        * The side length of that square is worldSize
        * Changing the scalar multiplier in this variable will affect amount of possible locations for planets
        * As a result, the spacing of the planets will change
        * For instance, decreasing the scalar multiplier will make the planets closer together by decreasing the amount of possible locations
        */
        val worldSize = 8 * numPlanets
        //The grid is a flattened array where every worldSize elements represent a row
        val probabilities = Array(worldSize * worldSize) { 1.0 }
        for (i in 0 until numPlanets) {
            //The following code chooses a new location for a planet
            //Create an array where each element is the sum of the first n elements of probabilities
            val cumulativeProbabilities = probabilities.clone()
            for (j in 1 until probabilities.size) {
                cumulativeProbabilities[j] += cumulativeProbabilities[j - 1]
            }
            //Choose a random number between 0 and the sum of all the probabilities
            val chosenCumulative = Math.random() * probabilities.sum()
            //Then the first number greater than that number is the at the index of the chosen planet
            val chosenLocation = cumulativeProbabilities.indexOfFirst { it > chosenCumulative }
            //Because bigger probabilities result in bigger gaps between adjacent elements in the cumulative
            //probabilities list, the chance that a location is chosen is proportional to the fraction of the total sum
            //its probability takes up

            //Setting the probability of that location to 0 ensures we cannot generate new planets there, and it is
            //also useful for determining the final locations of the planets
            probabilities[chosenLocation] = 0.0

            //Update probabilities
            for (j in 0 until probabilities.size) {
                //j / worldSize - j % worldSize is a conversion from the flattened list to a square grid coordinate system where
                //j / worldSize is row and j % worldSize is column
                //We add a scalar factor based on the Manhattan distance between each tile and the new planet to its probability
                //If the tile in question is already a planet, do nothing
                probabilities[j] = if (probabilities[j] != 0.0) probabilities[j] + Math.pow((Math.abs(j / worldSize - chosenLocation / worldSize) + Math.abs(j % worldSize  - chosenLocation % worldSize)).toDouble(), 1.0) else 0.0
            }
        }
        //When we're finished, every coordinate with a zero probability is market for becoming a planet
        //For each one, add a planet to the list of planets after scaling coordinates to be between 0 and 1
        //0.5 / worldSize accounts for the planet being at the center of the square defined by coordinates instead of its corner
        (0 until probabilities.size)
                .filter { probabilities[it] == 0.0 }
                .mapTo(planets) { Planet((it / worldSize).toFloat() / worldSize + 0.5f / worldSize, (it % worldSize).toFloat() / worldSize + 0.5f / worldSize, ((0.15 + Math.random() * 0.35) / worldSize).toFloat()) }
        /**
         * Edge generation:
         * Goes through each planet in a random order and creates cosmic highways (connections) between planets within .2 of world size distance
         * Doesn't create highways that cross other highways TODO: Doesn't work quite perfectly
         */
        for (p0 in planets.shuffled()) {
            var highwayChance = 1f
            for (p1 in planets) {
                //If the distance between the two planets is greater than .2, go to next planet
                if (sqrt((p0.x - p1.x).pow(2) + (p0.y - p1.y).pow(2)) >= 0.2 || Math.random() > highwayChance) {
                    continue
                }
                //If the current planets can have a path that doesn't intersect an existing highway, or already is an existing highway, or intersect a planet, make a highway
                if (!highways.any {
                            doSegmentsIntersect(p0.x, p0.y, p1.x, p1.y, it.p0.x, it.p0.y, it.p1.x, it.p1.y) || //Highways crosses existing highway
                            (it.p0 == p0 && it.p1 == p1) || (it.p0 == p1 && it.p1 == p0) //Highway already exists but with p0 and p1 switched around
                        } && !planets.filter { it != p0 && it != p1 }.any {
                            Intersector.distanceSegmentPoint(p0.x, p0.y, p1.x, p1.y, it.x, it.y) <= it.radius //Highway doesn't intersect planet
                        }) {
                    highways.add(CosmicHighway(p0, p1))
                    highwayChance *= 1f
                }
            }
        }
    }

    /**
     * Returns whether an intersection happens that isn't an intersection at the endpoints
     * Segments are p0 -> p1 and p2 -> p3
     */
    private fun doSegmentsIntersect(p0x: Float, p0y: Float, p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float): Boolean {
        val intersectionPoint = Vector2()
        val intersect = Intersector.intersectSegments(p0x, p0y, p1x, p1y, p2x, p2y, p3x, p3y, intersectionPoint)
        //Returns whether it intersects and that the intersection point isn't an endpoint
        return intersect && !(
                (intersectionPoint.x == p0x && intersectionPoint.y == p0y) ||
                (intersectionPoint.x == p1x && intersectionPoint.y == p1y) ||
                (intersectionPoint.x == p2x && intersectionPoint.y == p2y) ||
                (intersectionPoint.x == p3x && intersectionPoint.y == p3y)
        )
    }

}
