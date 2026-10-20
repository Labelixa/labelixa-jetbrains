package com.labelixa.jetbrains;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.psi.PsiFile;

import java.lang.reflect.Method;

/**
 * Platform calls whose non-deprecated form differs between the oldest
 * supported IDE (2024.1) and current ones.
 *
 * <p>Written in Java on purpose: a Kotlin call to {@code CredentialAttributes(name)}
 * compiles to the synthetic default-arguments constructor, which newer
 * platforms deprecate. Java binds to the plain {@code (String)} constructor,
 * which exists and is not deprecated on every supported version.
 */
final class PlatformCompat {
    private PlatformCompat() {
    }

    static CredentialAttributes credentialAttributes(String serviceName) {
        return new CredentialAttributes(serviceName);
    }

    /**
     * Re-runs highlighting for one file. Newer platforms replace
     * {@code restart(PsiFile)} with {@code restart(PsiFile, Object reason)};
     * the old one is used only where the new one does not exist.
     */
    static void restartHighlighting(DaemonCodeAnalyzer analyzer, PsiFile file) {
        try {
            Method withReason = analyzer.getClass().getMethod("restart", PsiFile.class, Object.class);
            withReason.invoke(analyzer, file, "Labelixa: Validate ZPL");
            return;
        } catch (NoSuchMethodException ignored) {
            // older platform: fall through
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        try {
            analyzer.getClass().getMethod("restart", PsiFile.class).invoke(analyzer, file);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
