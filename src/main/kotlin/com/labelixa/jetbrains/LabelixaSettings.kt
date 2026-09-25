package com.labelixa.jetbrains

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/**
 * Plugin settings. Everything but the API key is an ordinary setting; the
 * key lives in the IDE's password safe so it is never written into a
 * settings file that could be committed or synced.
 */
@Service(Service.Level.APP)
@State(name = "LabelixaSettings", storages = [Storage("labelixa.xml")])
class LabelixaSettings : PersistentStateComponent<LabelixaSettings.State> {
    class State {
        var baseUrl: String = Core.DEFAULT_BASE_URL
        var dpmm: Int = 8
        var widthIn: String = "4"
        var heightIn: String = "6"
        var lintWhileTyping: Boolean = false
    }

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    val baseUrl: String get() = state.baseUrl.trim().ifEmpty { Core.DEFAULT_BASE_URL }
    val dpmm: Int get() = state.dpmm
    val widthIn: Double get() = state.widthIn.trim().toDoubleOrNull() ?: 4.0
    val heightIn: Double get() = state.heightIn.trim().toDoubleOrNull() ?: 6.0
    val lintWhileTyping: Boolean get() = state.lintWhileTyping

    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes()) ?: ""
        set(value) {
            val v = value.trim()
            PasswordSafe.instance.set(credentialAttributes(), if (v.isEmpty()) null else Credentials("api-key", v))
        }

    private fun credentialAttributes() =
        CredentialAttributes(generateServiceName("Labelixa", "apiKey"))

    companion object {
        fun get(): LabelixaSettings = service()
    }
}

class LabelixaConfigurable : BoundConfigurable("Labelixa") {
    override fun createPanel(): DialogPanel {
        val s = LabelixaSettings.get()
        val st = s.state
        return panel {
            row("API key:") {
                passwordField()
                    .columns(40)
                    .bindText({ s.apiKey }, { s.apiKey = it })
                    .comment("Optional. Without a key the free, rate-limited anonymous tier is used. " +
                        "Stored in the IDE password safe, not in a settings file.")
            }
            row("Base URL:") {
                textField().columns(40).bindText(st::baseUrl)
            }
            row("Print density (dots/mm):") {
                intTextField(range = 6..24).bindIntText(st::dpmm)
                    .comment("8 = 203 dpi, 12 = 300 dpi, 24 = 600 dpi")
            }
            row("Label width (in):") {
                textField().columns(8).bindText(st::widthIn)
            }
            row("Label height (in):") {
                textField().columns(8).bindText(st::heightIn)
            }
            row {
                checkBox("Lint while typing").bindSelected(st::lintWhileTyping)
                    .comment("Off by default: nothing is sent to the API until you run an action. " +
                        "When on, the file is re-checked after you stop editing; validation does not consume label quota.")
            }
        }
    }
}
