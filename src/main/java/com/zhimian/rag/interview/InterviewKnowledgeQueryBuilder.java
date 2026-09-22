package com.zhimian.rag.interview;

import com.zhimian.model.interview.InterviewPlanItem;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


/**
 * 示例输出:
 * moduleName = MySQL
 * skill = 索引、事务和慢查询
 * questionType = 工程实践
 * question = 如果订单或业务记录表数据量快速增长，你会怎样设计索引和查询？
 *
 *
 * 得到:
 * MySQL；索引、事务和慢查询；工程实践；如果订单或业务记录表数据量快速增长，你会怎样设计索引和查询？
 */
@Component
public class InterviewKnowledgeQueryBuilder {
    private static final int MAX_QUERY_LENGTH = 500;

    public String build(String question, InterviewPlanItem item){
        if(!StringUtils.hasText(question)){
            throw new IllegalStateException("面试题目不能为空");
        }

        StringBuilder query = new StringBuilder();
        if(item !=null){
            append(query,item.moduleName());
            append(query,item.skill());
            append(query,item.questionType());
        }
        append(query,question.strip());

        String value = query.toString();
        return value.length() <= MAX_QUERY_LENGTH
                ? value
                : value.substring(0,MAX_QUERY_LENGTH);
    }

    private void append(StringBuilder builder, String value) {
        if(!StringUtils.hasText(value)){
            return;
        }
        if(!builder.isEmpty()){
            builder.append("；");
        }
        builder.append(value.strip());
    }
}
