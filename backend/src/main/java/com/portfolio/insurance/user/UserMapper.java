package com.portfolio.insurance.user;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(AppUser user);

    List<UserDto> toDtos(List<AppUser> users);
}
