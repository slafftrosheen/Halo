package com.halo.app.data.toolkit

import android.app.Application
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class HaloCurrencyRatesTest {
    @Test fun publishedTableProvidesDatedCrossRatesWithoutInventingAnAbsentPair() {
        val reference = HaloCurrencyRates.parse("""
            <gesmes:Envelope xmlns:gesmes="http://www.gesmes.org/xml/2002-08-01"
                xmlns="http://www.ecb.int/vocabulary/2002-08-01/eurofxref">
              <Cube><Cube time="2025-12-19">
                <Cube currency="USD" rate="1.20"/>
                <Cube currency="PLN" rate="4.20"/>
              </Cube></Cube>
            </gesmes:Envelope>
        """.trimIndent())

        assertEquals(LocalDate.parse("2025-12-19"), reference.date)
        val cross = reference.crossRate(CalcUnit.USD, CalcUnit.PLN)
        assertEquals("3.5", cross)
        assertEquals("70", HaloCalcEngine.display(HaloCalcEngine.convert("20", CalcUnit.USD, CalcUnit.PLN, cross)))
        assertNull(reference.crossRate(CalcUnit.RUB, CalcUnit.PLN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDocumentTypeDeclarations() {
        HaloCurrencyRates.parse("<!DOCTYPE bad><Cube time=\"2025-12-19\"><Cube currency=\"USD\" rate=\"1\"/></Cube>")
    }
}
