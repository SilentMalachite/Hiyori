package app.testutil;

import javafx.application.Platform;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JavaFX Toolkitを初期化するためのJUnit5拡張機能
 */
public class JavaFxExtension implements BeforeAllCallback {
    private static boolean initialized = false;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!initialized) {
            try {
                Platform.startup(() -> {});
                initialized = true;
            } catch (IllegalStateException e) {
                // すでに初期化されている場合は無視
                initialized = true;
            }
        }
    }
}