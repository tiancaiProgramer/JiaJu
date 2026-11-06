package dev.homecontrol.app;

/** A small live jitter buffer; re-anchor after stalls instead of accumulating delay. */
final class CameraFrameClock {
    private static final long BUFFER_NS=100_000_000L, LATE_NS=180_000_000L;
    private long originNs,originUs,previousUs=Long.MIN_VALUE;
    long schedule(long ptsUs,long nowNs){
        if(previousUs==Long.MIN_VALUE||ptsUs<previousUs||ptsUs-previousUs>2_000_000L){originUs=ptsUs;originNs=nowNs+BUFFER_NS;}
        long target=originNs+(ptsUs-originUs)*1000L;
        if(target<nowNs-LATE_NS||target>nowNs+500_000_000L){originUs=ptsUs;originNs=nowNs+BUFFER_NS;target=originNs;}
        previousUs=ptsUs;return Math.max(nowNs,target);
    }
}
