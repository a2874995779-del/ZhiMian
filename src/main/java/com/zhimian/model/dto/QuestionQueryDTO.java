package com.zhimian.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class QuestionQueryDTO {
    @Min(value = 1,message = "pageNum 最小为1")
    private Integer pageNum=1;
    @Min(value = 1,message = "pageSize最小为1")
    @Max(value = 50,message = "pageSize最大为50")
    private Integer pageSize=10;
    private Long categoryId;
    private Long tagId;
    private Integer difficulty;
    private String keyword;

    public int getOffset(){
        return (pageNum-1)*pageSize;
    }
}
