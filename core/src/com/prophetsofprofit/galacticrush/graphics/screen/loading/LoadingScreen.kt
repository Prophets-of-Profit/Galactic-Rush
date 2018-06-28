package com.prophetsofprofit.galacticrush.graphics.screen.loading

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.utils.Array
import com.prophetsofprofit.galacticrush.Main
import ktx.app.KtxScreen
import ktx.app.use

/**
 * A set of behaviour common to loading screens regardless of them being client or host
 */
abstract class LoadingScreen(val game: Main) : KtxScreen {

    //Measures time spent loading
    val animation = Animation<Texture>(2.25f, Array(Array(28) { Texture("animations/loading/LoadingScreen$it.png") }))
    //Whether the screen is done loading or not
    var isDone = false
    //The total time spent loading
    var elapsedTime = 0f

    /**
     * Starts initializing the galaxy and sets the screen coordinate system with the camera
     */
    init {
        this.game.camera.setToOrtho(false, 1600f, 900f)
        this.game.batch.projectionMatrix = this.game.camera.combined
        Thread {
            this.load()
            this.isDone = true
        }.start()
    }

    /**
     * Does the loading for whatever this loading screen is meant to do or load
     */
    abstract fun load()

    /**
     * What the screen should do when finished loading
     */
    abstract fun onLoad()

    /**
     * How the screen is drawn
     * Draws an image of the animation every frame
     */
    override fun render(delta: Float) {
        //Clears the screen
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
        //Checks if the screen is done loading and then performs the onLoad action if loading is done
        if (this.isDone) {
            this.onLoad()
        }
        //Updates the time waited and updates the image used in the loading screen for the animation
        this.elapsedTime += delta
        this.game.batch.use {
            it.draw(animation.getKeyFrame(this.elapsedTime, true), 500f, 150f, 600f, 600f, 0, 0, 64, 64, false, false)
        }
    }

}