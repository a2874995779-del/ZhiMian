package com.zhimian.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class DashboardVO {
    private DailyProgressVO today;
    private Integer streakDays;
    private Integer totalSolved;
    private Integer weeklyDelta;
    private List<TrendPointVO> trend;

    @Data
    public static class DailyProgressVO {
        private Integer done;
        private Integer goal;
    }

    @Data
    public static class TrendPointVO {
        private String label;
        private Integer passProbability;
    }
}
