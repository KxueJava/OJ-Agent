package com.codeagentoj.server.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM users WHERE username = #{value} OR email = #{value} LIMIT 1")
    User findByUsernameOrEmail(String value);
}
