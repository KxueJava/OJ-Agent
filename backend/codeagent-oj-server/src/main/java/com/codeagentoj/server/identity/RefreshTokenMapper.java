package com.codeagentoj.server.identity;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
    @Select("SELECT * FROM refresh_tokens WHERE token_hash = #{hash} LIMIT 1")
    RefreshToken findByHash(String hash);
}
