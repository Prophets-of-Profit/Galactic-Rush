package com.prophetsofprofit.galacticrush.graphics.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.prophetsofprofit.galacticrush.Main
import com.prophetsofprofit.galacticrush.Networker
import com.prophetsofprofit.galacticrush.graphics.screen.loading.HostLoadingScreen
import com.prophetsofprofit.galacticrush.logic.player.LocalPlayer
import ktx.app.use
import ktx.scene2d.Scene2DSkin

/**
 * The screen that handles all main menu actions and display and layout and such
 */
class MainMenuScreen(game: Main) : GalacticRushScreen(game, arrayOf("meta/TheIntergalactic.mp3")) {

    val backgroundTexture = Texture("meta/Background.png")
    val titleTexture = Texture("meta/Title.png")
    var timeSpent = 0f

    init {
        val hostGameButton = TextButton("Host a Game", Scene2DSkin.defaultSkin)
        val joinGameButton = TextButton("Join a Game", Scene2DSkin.defaultSkin)
        val optionsButton = TextButton("Options", Scene2DSkin.defaultSkin)
        hostGameButton.setPosition(this.uiContainer.width * 0.1f, this.uiContainer.height * 0.3f, Align.center)
        joinGameButton.setPosition(this.uiContainer.width * 0.1f, this.uiContainer.height * 0.2f, Align.center)
        optionsButton.setPosition(this.uiContainer.width * 0.1f, this.uiContainer.height * 0.1f, Align.center)
        this.uiContainer.addActor(hostGameButton)
        this.uiContainer.addActor(joinGameButton)
        this.uiContainer.addActor(optionsButton)
        hostGameButton.addListener(object: ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.screen = HostLoadingScreen(game, Array(1) { LocalPlayer(0) })
            }
        })
        optionsButton.addListener(object: ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                game.screen = OptionsScreen(game) { game.screen = MainMenuScreen(game) }
            }
        })
        Gdx.input.inputProcessor = this.uiContainer
        Networker.reset()
    }

    /**
     * The periodic update/draw method
     * Passes in the amount of time since last render
     */
    override fun draw(delta: Float) {
        timeSpent += delta
        this.game.batch.use {
            it.draw(backgroundTexture, -200f * (this.timeSpent % 8f), 0f, 1600f, 900f)
            it.draw(backgroundTexture, -200f * (this.timeSpent % 8f) + 1600, 0f, 1600f, 900f)
            it.draw(titleTexture, 0f, 400f, 1600f, 500f)
        }
    }

    /**
     * Disposes of held textures upon screen leave
     */
    override fun leave() {
        this.titleTexture.dispose()
        this.backgroundTexture.dispose()
    }

}
