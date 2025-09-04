package app.util;

import javafx.animation.PauseTransition;
import javafx.util.Duration;

/**
 * Simple JavaFX-based debouncer.
 */
public class Debouncer {
    private final PauseTransition pause;

    public Debouncer(double seconds) {
        this.pause = new PauseTransition(Duration.seconds(seconds));
    }

    public void call(Runnable action) {
        pause.setOnFinished(e -> action.run());
        pause.playFromStart();
    }
}

