package dev.studios.samimine

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import dev.studios.samimine.data.remote.PilotEnvironment

/** Existing measurement regression fixtures use a local-only composition, never production intents. */
class SamiMineTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        val options = arguments ?: Bundle()
        PilotEnvironment.demoTests = options.getString("pilotAuth") != "true"
        if (PilotEnvironment.demoTests) {
            val excluded = listOfNotNull(options.getString("notClass"), "dev.studios.samimine.PilotFirebaseFlowTest")
            options.putString("notClass", excluded.joinToString(","))
        }
        super.onCreate(options)
    }
}
