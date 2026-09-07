package com.zhimian.service.impl;

import com.zhimian.common.ErrorCode;
import com.zhimian.common.UserContext;
import com.zhimian.exception.BusinessException;
import com.zhimian.mapper.AnswerRecordMapper;
import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.UserMapper;
import com.zhimian.model.dto.AnswerDailyStatDTO;
import com.zhimian.model.dto.AnswerSubmitDTO;
import com.zhimian.model.dto.RankStatDTO;
import com.zhimian.model.entity.AnswerRecord;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.User;
import com.zhimian.model.vo.DashboardVO;
import com.zhimian.model.vo.RankVO;
import com.zhimian.service.AnswerService;
import com.zhimian.service.WrongQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnswerServiceImpl implements AnswerService {

    private final AnswerRecordMapper answerRecordMapper;
    private final QuestionMapper questionMapper;
    private final UserMapper userMapper;
    private final WrongQuestionService wrongQuestionService;

    private static final int DAILY_GOAL = 10;
    private static final String[] WEEK_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    @Transactional
    @Override
    public void submitAnswer(long questionId, AnswerSubmitDTO dto) {
        Question question = questionMapper.selectById(questionId);
        if(question == null){
            throw new BusinessException(ErrorCode.NOT_FOUND,"题目不存在");
        }
        Long userId = UserContext.getUserId();
        AnswerRecord record = new AnswerRecord();
        record.setUserId(userId);
        record.setQuestionId(questionId);
        record.setResult(dto.getResult());
        answerRecordMapper.insert(record);
        wrongQuestionService.recordAnswer(questionId,dto.getResult());
    }

    @Override
    public List<RankVO> getRank(String type, int limit) {
        if(limit <= 0){
            return List.of();
        }
        if (!"total".equals(type) && !"daily".equals(type)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "排行榜类型只能是total或daily");
        }
        int safeLimit = Math.min(limit, 100);
        LocalDateTime start = null;
        LocalDateTime end = null;
        if ("daily".equals(type)) {
            LocalDate today = LocalDate.now();
            start = today.atStartOfDay();
            end = today.plusDays(1).atStartOfDay();
        }
        List<RankStatDTO> stats = answerRecordMapper.selectCorrectRank(start, end, safeLimit);
        if (stats.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = stats.stream().map(RankStatDTO::getUserId).toList();
        Map<Long,String> nicknameMap = new HashMap<>();
        for(User u : userMapper.selectByIds(userIds)){
            nicknameMap.put(u.getId(),u.getNickname());
        }
        List<RankVO> result = new ArrayList<>();
        int rank=1;
        for(RankStatDTO stat : stats){
            Long userId = stat.getUserId();
            RankVO vo = new RankVO();
            vo.setRank(rank++);
            vo.setUserId(userId);
            vo.setNickname(nicknameMap.get(userId));
            vo.setCount(stat.getCount());
            result.add(vo);
        }
        return result;
    }

    @Override
    public DashboardVO getDashboard() {
        Long userId = UserContext.getUserId();
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        LocalDate startOfLastWeek = startOfWeek.minusDays(7);

        int todayDone = answerRecordMapper.countCorrectByUserBetween(userId, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int totalSolved = answerRecordMapper.countCorrectByUser(userId);
        int thisWeekSolved = answerRecordMapper.countCorrectByUserBetween(userId, startOfWeek.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int lastWeekSolved = answerRecordMapper.countCorrectByUserBetween(userId, startOfLastWeek.atStartOfDay(), startOfWeek.atStartOfDay());

        DashboardVO vo = new DashboardVO();
        DashboardVO.DailyProgressVO todayProgress = new DashboardVO.DailyProgressVO();
        todayProgress.setDone(todayDone);
        todayProgress.setGoal(DAILY_GOAL);
        vo.setToday(todayProgress);
        vo.setTotalSolved(totalSolved);
        vo.setWeeklyDelta(thisWeekSolved - lastWeekSolved);
        vo.setStreakDays(calculateStreakDays(userId, today));
        vo.setTrend(buildWeeklyTrend(userId, startOfWeek));
        return vo;
    }

    private int calculateStreakDays(Long userId, LocalDate today) {
        Set<LocalDate> dates = new HashSet<>(answerRecordMapper.selectCorrectDates(userId));
        int streak = 0;
        LocalDate cursor = today;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private List<DashboardVO.TrendPointVO> buildWeeklyTrend(Long userId, LocalDate startOfWeek) {
        LocalDateTime start = startOfWeek.atStartOfDay();
        LocalDateTime end = startOfWeek.plusDays(7).atStartOfDay();
        Map<LocalDate, AnswerDailyStatDTO> statMap = new HashMap<>();
        for (AnswerDailyStatDTO stat : answerRecordMapper.selectDailyStats(userId, start, end)) {
            statMap.put(stat.getAnswerDate(), stat);
        }

        List<DashboardVO.TrendPointVO> trend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startOfWeek.plusDays(i);
            AnswerDailyStatDTO stat = statMap.get(date);
            int total = stat == null || stat.getTotalCount() == null ? 0 : stat.getTotalCount();
            int correct = stat == null || stat.getCorrectCount() == null ? 0 : stat.getCorrectCount();

            DashboardVO.TrendPointVO point = new DashboardVO.TrendPointVO();
            point.setLabel(WEEK_LABELS[i]);
            point.setPassProbability(total == 0 ? 0 : (int) Math.round(correct * 100.0 / total));
            trend.add(point);
        }
        return trend;
    }
}
