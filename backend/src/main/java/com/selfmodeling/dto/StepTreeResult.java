package com.selfmodeling.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class StepTreeResult {
    private List<FlowNode> nodes;
    private List<FlowEdge> edges;

    @Data
    public static class FlowNode {
        private Long id;
        private String stepName;
        private String stepType;
        private int x;
        private int y;
        private Map<String, Object> config;
    }

    @Data
    public static class FlowEdge {
        private Long source;
        private Long target;
        private String label;
    }
}
