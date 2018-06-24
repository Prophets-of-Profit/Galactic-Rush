package com.prophetsofprofit.galacticrush.logic.player

import com.prophetsofprofit.galacticrush.Networker
import com.prophetsofprofit.galacticrush.logic.Game

/**
 * A player that plays on a different machine on the network
 * Can never be the host player, but will be initialized on host player's machine
 */
class NetworkPlayer(id: Int, val connectionId: Int): Player(id) {

    /**
     * A method that gets called form the clientside that sends a change object to the game
     */
    override fun submitChanges() {
        Networker.getClient().sendTCP(this.currentChanges)
    }

    /**
     * A method that gets called from the serverside that sends an updated game to the client
     */
    override fun receiveNewGameState(newGame: Game) {
        this.game = newGame
        Networker.getServer().sendToTCP(this.connectionId, this)
    }

}