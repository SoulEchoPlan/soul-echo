package com.dotlinea.soulecho.mapper;

import com.dotlinea.soulecho.entity.Characters;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author lifeng
* @description 针对表【characters】的数据库操作Mapper
* @createDate 2025-09-25 16:30:11
* @Entity com.dotlinea.soulecho.entity.Characters
*/
@Mapper
public interface CharactersMapper extends BaseMapper<Characters> {

}




