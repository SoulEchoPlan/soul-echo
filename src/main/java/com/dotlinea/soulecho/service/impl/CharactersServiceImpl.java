package com.dotlinea.soulecho.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dotlinea.soulecho.entity.Characters;
import com.dotlinea.soulecho.mapper.CharactersMapper;
import com.dotlinea.soulecho.service.CharactersService;
import org.springframework.stereotype.Service;

/**
* @author lifeng
* @description 针对表【characters】的数据库操作Service实现
* @createDate 2025-09-25 16:30:11
*/
@Service
public class CharactersServiceImpl extends ServiceImpl<CharactersMapper, Characters>
    implements CharactersService{

}




