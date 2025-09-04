package app.model;

public class Event {
    private long id;
    private String title;
    private long startEpochSec;
    private long endEpochSec;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public long getStartEpochSec() { return startEpochSec; }
    public void setStartEpochSec(long startEpochSec) { this.startEpochSec = startEpochSec; }
    public long getEndEpochSec() { return endEpochSec; }
    public void setEndEpochSec(long endEpochSec) { this.endEpochSec = endEpochSec; }
}

