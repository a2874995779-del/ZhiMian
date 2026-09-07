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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.zhimian.service.WrongQuestionService;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerServiceImplTest {

    @Mock
    private AnswerRecordMapper answerRecordMapper;
    @Mock
    private QuestionMapper questionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private WrongQuestionService wrongQuestionService;

    private AnswerServiceImpl answerService;

    @BeforeEach
    void setUp() {
        answerService = new AnswerServiceImpl(
                answerRecordMapper,
                questionMapper,
                userMapper,
                wrongQuestionService
        );
        UserContext.set(42L, "user", "jti", System.currentTimeMillis() + 3_600_000);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    void submitCorrectAnswerPersistsRecord() {
        Question question = new Question();
        question.setId(100L);
        when(questionMapper.selectById(100L)).thenReturn(question);

        AnswerSubmitDTO dto = new AnswerSubmitDTO();
        dto.setResult(1);

        answerService.submitAnswer(100L, dto);

        ArgumentCaptor<AnswerRecord> recordCaptor = ArgumentCaptor.forClass(AnswerRecord.class);
        verify(answerRecordMapper).insert(recordCaptor.capture());
        AnswerRecord record = recordCaptor.getValue();
        assertThat(record.getUserId()).isEqualTo(42L);
        assertThat(record.getQuestionId()).isEqualTo(100L);
        assertThat(record.getResult()).isEqualTo(1);

        verify(wrongQuestionService).recordAnswer(100L, 1);
    }

    @Test
    void submitWrongAnswerDoesNotUpdateRank() {
        Question question = new Question();
        question.setId(100L);
        when(questionMapper.selectById(100L)).thenReturn(question);

        AnswerSubmitDTO dto = new AnswerSubmitDTO();
        dto.setResult(0);

        answerService.submitAnswer(100L, dto);

        verify(answerRecordMapper).insert(any(AnswerRecord.class));
        verify(wrongQuestionService).recordAnswer(100L, 0);
    }

    @Test
    void submitMissingQuestionThrowsNotFound() {
        when(questionMapper.selectById(404L)).thenReturn(null);

        AnswerSubmitDTO dto = new AnswerSubmitDTO();
        dto.setResult(1);

        assertThatThrownBy(() -> answerService.submitAnswer(404L, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.NOT_FOUND.getCode());
        verify(answerRecordMapper, never()).insert(any());
    }

    @Test
    void getRankMapsDatabaseStatsToUserNicknames() {
        when(answerRecordMapper.selectCorrectRank(null, null, 2))
                .thenReturn(List.of(rankStat(7L, 12), rankStat(9L, 8)));

        User user7 = user(7L, "小智");
        User user9 = user(9L, "面霸");
        when(userMapper.selectByIds(List.of(7L, 9L))).thenReturn(List.of(user7, user9));

        List<RankVO> ranks = answerService.getRank("total", 2);

        assertThat(ranks).hasSize(2);
        assertThat(ranks.get(0).getRank()).isEqualTo(1);
        assertThat(ranks.get(0).getUserId()).isEqualTo(7L);
        assertThat(ranks.get(0).getNickname()).isEqualTo("小智");
        assertThat(ranks.get(0).getCount()).isEqualTo(12);
        assertThat(ranks.get(1).getRank()).isEqualTo(2);
        assertThat(ranks.get(1).getCount()).isEqualTo(8);
    }

    @Test
    void getRankReturnsEmptyWhenLimitIsInvalid() {
        assertThat(answerService.getRank("total", 0)).isEmpty();
        verify(answerRecordMapper, never()).selectCorrectRank(any(), any(), any(Integer.class));
    }

    @Test
    void getRankRejectsUnknownType() {
        assertThatThrownBy(() -> answerService.getRank("weekly", 10))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PARAMS_ERROR.getCode());
        verify(answerRecordMapper, never()).selectCorrectRank(any(), any(), any(Integer.class));
    }

    @Test
    void getRankCapsPublicLimit() {
        when(answerRecordMapper.selectCorrectRank(null, null, 100)).thenReturn(List.of());

        assertThat(answerService.getRank("total", 10_000)).isEmpty();

        verify(answerRecordMapper).selectCorrectRank(null, null, 100);
    }

    @Test
    void getDashboardBuildsProgressTrendAndStreakFromRecords() {
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.minusDays(today.getDayOfWeek().getValue() - 1L);

        when(answerRecordMapper.countCorrectByUserBetween(eq(42L), any(), any()))
                .thenReturn(3, 8, 5);
        when(answerRecordMapper.countCorrectByUser(42L)).thenReturn(20);
        when(answerRecordMapper.selectCorrectDates(42L)).thenReturn(List.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)));
        when(answerRecordMapper.selectDailyStats(eq(42L), any(), any()))
                .thenReturn(List.of(dailyStat(startOfWeek, 4, 3)));

        DashboardVO dashboard = answerService.getDashboard();

        assertThat(dashboard.getToday().getDone()).isEqualTo(3);
        assertThat(dashboard.getToday().getGoal()).isEqualTo(10);
        assertThat(dashboard.getTotalSolved()).isEqualTo(20);
        assertThat(dashboard.getWeeklyDelta()).isEqualTo(3);
        assertThat(dashboard.getStreakDays()).isEqualTo(3);
        assertThat(dashboard.getTrend()).hasSize(7);
        assertThat(dashboard.getTrend().get(0).getLabel()).isEqualTo("周一");
        assertThat(dashboard.getTrend().get(0).getPassProbability()).isEqualTo(75);
    }

    private User user(Long id, String nickname) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

    private AnswerDailyStatDTO dailyStat(LocalDate date, int total, int correct) {
        AnswerDailyStatDTO stat = new AnswerDailyStatDTO();
        stat.setAnswerDate(date);
        stat.setTotalCount(total);
        stat.setCorrectCount(correct);
        return stat;
    }

    private RankStatDTO rankStat(Long userId, int count) {
        RankStatDTO stat = new RankStatDTO();
        stat.setUserId(userId);
        stat.setCount(count);
        return stat;
    }
}
