package com.example.bai1.fixture

import android.os.Bundle
import android.util.Base64
import android.util.Xml
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.StringWriter

/** Real JUnit execution events exported over instrumentation status, no device file access needed. */
class DeviceXmlListener : RunListener() {
    private data class Case(val description: Description, var error: String? = null, var ignored: Boolean = false)
    private val cases = linkedMapOf<String, Case>()
    private fun key(d: Description) = d.className + "#" + d.methodName
    @Synchronized override fun testStarted(description: Description) { cases[key(description)] = Case(description) }
    @Synchronized override fun testFailure(failure: Failure) {
        cases.getOrPut(key(failure.description)) { Case(failure.description) }.error = failure.trace
    }
    @Synchronized override fun testIgnored(description: Description) { cases[key(description)] = Case(description, ignored = true) }
    @Synchronized override fun testAssumptionFailure(failure: Failure) {
        cases.getOrPut(key(failure.description)) { Case(failure.description) }.ignored = true
    }
    @Synchronized override fun testRunFinished(result: Result) {
        val out = StringWriter()
        val xml = Xml.newSerializer().apply { setOutput(out); startDocument("UTF-8", true) }
        xml.startTag(null, "testsuite")
        xml.attribute(null, "name", "P5E01DeviceRuntime")
        xml.attribute(null, "tests", cases.size.toString())
        xml.attribute(null, "failures", cases.values.count { it.error != null }.toString())
        xml.attribute(null, "errors", "0")
        xml.attribute(null, "skipped", cases.values.count { it.ignored }.toString())
        xml.attribute(null, "junitRunCount", result.runCount.toString())
        for (case in cases.values) {
            xml.startTag(null, "testcase")
            xml.attribute(null, "classname", case.description.className ?: "unknown")
            xml.attribute(null, "name", case.description.methodName ?: "initialization")
            case.error?.let { xml.startTag(null, "failure"); xml.text(it); xml.endTag(null, "failure") }
            if (case.ignored) { xml.startTag(null, "skipped"); xml.endTag(null, "skipped") }
            xml.endTag(null, "testcase")
        }
        xml.endTag(null, "testsuite"); xml.endDocument()
        InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
            putString("p5e01XmlBase64", Base64.encodeToString(out.toString().toByteArray(), Base64.NO_WRAP))
        })
    }
}
