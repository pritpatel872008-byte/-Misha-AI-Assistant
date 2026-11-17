package com.misha.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalBrainTest {
    private val brain = LocalBrain()
    @Test fun flashlightCommandsAreRecognized() {
        assertEquals(LocalBrain.TorchAction.ON, brain.torchAction("Turn on the flashlight"))
        assertEquals(LocalBrain.TorchAction.OFF, brain.torchAction("Turn off the flashlight"))
        assertEquals(LocalBrain.TorchAction.STATUS, brain.torchAction("Is the flashlight on?"))
    }
    @Test fun unrelatedTextIsNotAFlashlightCommand() {
        assertNull(brain.torchAction("Tell me about the weather"))
    }
}
