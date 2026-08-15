package edu.eci.arsw.warehouse.core;

/**
 * Starter pause/resume control.
 *
 * This version intentionally uses active waiting so students can replace it with
 * a monitor-based design using synchronized + wait()/notifyAll().
 */
public class SimulationControl {

    private boolean paused;

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    public synchronized void awaitIfPaused() {
        // TODO LAB 2: Completed
        while (paused) {
            try {
                wait();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
            
        }
    }

    public synchronized boolean isPaused() {
        return paused;
    }
}
