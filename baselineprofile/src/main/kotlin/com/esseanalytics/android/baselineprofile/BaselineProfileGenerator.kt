package com.esseanalytics.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Perfil inicial, seguro y reproducible: optimiza el arranque y la composición
 * de Inicio sin depender de que haya una cuenta de prueba autenticada.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = "com.esseanalytics.android",
        includeInStartupProfile = true,
    ) {
        startActivityAndWait()
    }
}
