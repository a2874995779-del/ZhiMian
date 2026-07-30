package com.zhimian.mapper;

import com.zhimian.model.entity.InterviewSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InterviewSessionMapper {
    long countInProgress(@Param("userId") Long userId);

    void insert(InterviewSession session);

    InterviewSession selectById(@Param("id") Long sessionId);

    long countByUserId(@Param("userId") Long userId);

    List<InterviewSession> selectPageByUserId(@Param("userId") Long userId,
                                              @Param("offset") int offset,
                                              @Param("pageSize") Integer pageSize);

    void updateStatus(@Param("id") Long id, @Param("status") Integer status);

    // 结束会话:置 status=2 并写入结束时间(end_time)。0→1 那步仍用 updateStatus,只有真正结束才写 end_time。
    void finishSession(@Param("id") Long id);
}
