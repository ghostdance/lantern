package org.lantern.state;

import org.codehaus.jackson.map.annotate.JsonView;
import org.lantern.state.Model.Persistent;
import org.lantern.state.Model.Run;

/**
 * Class representing all uploads and downloads data.
 */
public class Transfers {

    
    // sum of past runs
    private long historicalUpBytes = 0;
    private long historicalDownBytes = 0;
    
    @JsonView({Run.class})
    public long getBpsUp() {
        //return LanternHub.statsTracker().getUpBytesPerSecond();
        return 0;
    }
    
    @JsonView({Run.class})
    public long getBpsDn() {
        //return LanternHub.statsTracker().getDownBytesPerSecond();
        return 0;
    }
    
    @JsonView({Run.class})
    public long getBpsTotal() {
        return getBpsDn() + getBpsUp();
    }
    
    @JsonView({Run.class})
    public long getUpTotalThisRun() {
        //return LanternHub.statsTracker().getUpBytesThisRun();
        return 0;
    }
    
    @JsonView({Run.class})
    public long getDownTotalThisRun() {
        //return LanternHub.statsTracker().getDownBytesThisRun();
        return 0;
    }
    
    @JsonView({Run.class, Persistent.class})
    public long getUpTotalLifetime() {
        return getUpTotalThisRun() + historicalUpBytes;
    }
    
    public void setBytesUpLifetime(final long historicalUpBytes) {
        this.historicalUpBytes = this.historicalUpBytes + historicalUpBytes;
    }

    public void setUpTotalLifetime(final long value) {
        historicalUpBytes = value;
    }

    @JsonView({Run.class, Persistent.class})
    public long getDownTotalLifetime() {
        return getDownTotalThisRun() + historicalDownBytes;
    }

    public void setDownTotalLifetime(final long value) {
        historicalDownBytes = value;
    }
    
    @JsonView({Run.class})
    public long getBytesTotalLifetime() {
        return getDownTotalLifetime() + getUpTotalLifetime();
    }
}
