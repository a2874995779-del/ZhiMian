package com.zhimian.rag;

import com.zhimian.mapper.QuestionMapper;
import com.zhimian.mapper.UserMapper;
import com.zhimian.mapper.WrongQuestionMapper;
import com.zhimian.model.entity.Question;
import com.zhimian.model.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = WrongQuestionMapperIntegrationTest.TestApplication.class,
        properties = "spring.ai.vectorstore.redis.initialize-schema=false"
)
@EnabledIfEnvironmentVariable(
        named = "RUN_RAG_DATABASE_TESTS",
        matches = "true"
)
@Transactional
class WrongQuestionMapperIntegrationTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private WrongQuestionMapper wrongQuestionMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownershipQueryRequiresMatchingUserAndActiveQuestion() {
        User owner = insertUser("owner");
        User anotherUser = insertUser("other");
        Long categoryId = insertCategory();
        Question question = insertQuestion(owner.getId(), categoryId);

        assertThat(wrongQuestionMapper.existsByUserAndQuestion(
                owner.getId(), question.getId()
        )).isFalse();

        wrongQuestionMapper.recordWrong(owner.getId(), question.getId());

        assertThat(wrongQuestionMapper.existsByUserAndQuestion(
                owner.getId(), question.getId()
        )).isTrue();
        assertThat(wrongQuestionMapper.existsByUserAndQuestion(
                anotherUser.getId(), question.getId()
        )).isFalse();

        assertThat(questionMapper.logicalDelete(question.getId())).isEqualTo(1);
        assertThat(wrongQuestionMapper.existsByUserAndQuestion(
                owner.getId(), question.getId()
        )).isFalse();
    }

    private User insertUser(String prefix) {
        User user = new User();
        user.setUsername(prefix + "-" + UUID.randomUUID());
        user.setPassword("test-password-hash");
        user.setNickname(prefix);
        user.setRole("user");
        userMapper.insert(user);
        return user;
    }

    private Long insertCategory() {
        String name = "RAG-test-" + UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO category(name, parent_id, sort) VALUES (?, 0, 0)",
                name
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM category WHERE name = ?",
                Long.class,
                name
        );
    }

    private Question insertQuestion(Long userId, Long categoryId) {
        Question question = new Question();
        question.setTitle("RAG 错题归属测试-" + UUID.randomUUID());
        question.setContent("测试题干");
        question.setAnswer("测试答案");
        question.setDifficulty(1);
        question.setCategoryId(categoryId);
        question.setCreateUserId(userId);
        questionMapper.insert(question);
        return question;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = WrongQuestionMapper.class)
    static class TestApplication {
    }
}
