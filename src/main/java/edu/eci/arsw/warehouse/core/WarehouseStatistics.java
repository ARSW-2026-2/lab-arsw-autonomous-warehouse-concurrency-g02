package edu.eci.arsw.warehouse.core;

/**
 * Intentionally unsafe counters. ++ and += are not atomic read-modify-write operations.
 */
public class WarehouseStatistics {

    private int processedParcels;
    private long totalProcessingMillis;

    public void recordProcessed(long elapsedMillis) {
        synchronized (this) {
            int current = processedParcels;
            Thread.yield();
            processedParcels = current + 1;

            long accumulated = totalProcessingMillis;
            Thread.yield();
            totalProcessingMillis = accumulated + elapsedMillis;
        }
    }

    public synchronized int processedParcels() {
        return processedParcels;
    }

    public synchronized long totalProcessingMillis() {
        return totalProcessingMillis;
    }
}
