package models;

import java.util.ArrayList;
import java.util.List;

public class Stage {

    private String id;
    private String juncId;
    private List<Flow> flows;
    private Integer interlimit;
    private Boolean endcycle;
    
    public Stage(String id) {
        this.id = id;
        this.endcycle = false;
        this.juncId = null;
        this.interlimit = null;
        this.flows = new ArrayList<>();
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

    public Boolean getEndcycle() {
        return endcycle;
    }

    public void setEndcycle(Boolean endcycle) {
        this.endcycle = endcycle;
    }

    public List<Flow> getFlows() {
        return flows;
    }

    public void addFlow(Flow flow) {
        this.flows.add(flow);
    }

    public Integer getInterlimit() {
        return interlimit;
    }

    public void setInterlimit(Integer interlimit) {
        this.interlimit = interlimit;
    }

}
