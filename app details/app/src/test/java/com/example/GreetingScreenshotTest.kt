package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GreetingScreenshotTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captureGreeting() {
        composeTestRule.setContent {
            Greeting("Android")
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
