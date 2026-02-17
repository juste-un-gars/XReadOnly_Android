/**
 * @file InjectionScripts.kt
 * @description Loads CSS/JS from assets and provides injection helpers for the WebView.
 */
package com.example.xreadonly

import android.content.Context
import android.util.Log

object InjectionScripts {

    private const val TAG = "XReadOnly.Injection"

    /**
     * Reads a file from the assets folder as a String.
     * @param context Application context
     * @param fileName Asset file name (e.g. "inject.css")
     * @return File contents, or empty string on failure
     */
    fun loadAsset(context: Context, fileName: String): String {
        return try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to load asset: $fileName", e)
            }
            ""
        }
    }

    /**
     * Builds JavaScript that injects a <style> tag with the given CSS into the page.
     * @param css The CSS content to inject
     * @return JavaScript string ready for evaluateJavascript()
     */
    fun buildCssInjectionScript(css: String): String {
        val escaped = css
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        return """
            (function() {
                var style = document.createElement('style');
                style.id = 'xreadonly-css';
                style.textContent = '$escaped';
                document.head.appendChild(style);
            })();
        """.trimIndent()
    }

    /**
     * Wraps raw JS content so it is safe for evaluateJavascript().
     * The inject.js is already an IIFE, so this just ensures clean delivery.
     * @param js The JavaScript content from inject.js
     * @return JavaScript string ready for evaluateJavascript()
     */
    fun buildJsInjectionScript(js: String): String {
        return "(function(){$js})();"
    }
}
