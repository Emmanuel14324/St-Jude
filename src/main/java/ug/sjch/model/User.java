package ug.sjch.model;

public record User(
        long id,
        String username,
        String roleName,
        boolean active,
        Long staffId
) {
}
