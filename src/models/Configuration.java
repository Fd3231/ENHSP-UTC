package models;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configuration {

    private String id;
    private String juncId;
    private Map<String, List<ConfgreentimeTuple>> stages;

    public Configuration(String id) {
        this.id = id;
        this.juncId = null;
        this.stages = new LinkedHashMap<>();
    }

    public Configuration() {
        this.id = "";
        this.stages = null;
    }

    public String getId() {
        return id;
    }

    public String getJuncId() {
        return juncId;
    }

    public void setJuncId(String juncId) {
        this.juncId = juncId;
    }

    public Map<String, List<ConfgreentimeTuple>> getStages() {
        return stages;
    }

    public Set<String> getStagesId() {
        return stages.keySet();
    }

    public Integer getGreentime(String stageId) {
        return this.stages.get(stageId).get(0).getGreentime();
    }

    public void addStage(String stageId) {
        this.stages.put(stageId, new ArrayList<>());
    }

    public void addGreentime(String stageId, Integer greentime) {
        this.stages.get(stageId).add(new ConfgreentimeTuple(stageId, greentime));
    }

    public static class ConfgreentimeTuple {
        private String stageId;
        private int greentime;

        public ConfgreentimeTuple(String stageId, int greentime) {
            this.stageId = stageId;
            this.greentime = greentime;
        }

        public String getStageId() {
            return stageId;
        }

        public int getGreentime() {
            return greentime;
        }
    }
}

