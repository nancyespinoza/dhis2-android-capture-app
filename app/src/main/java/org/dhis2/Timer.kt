package org.dhis2

import android.util.Log

object Timer {

    var time : Long = 0

    fun start(){
        time = System.currentTimeMillis()
    }

    fun stop() {
        val finishTime = System.currentTimeMillis() - time
        Log.i ("Timing ", finishTime.toString())
    }
}
