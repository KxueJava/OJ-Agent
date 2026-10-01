package com.codeagentoj.server.profile;

import com.codeagentoj.server.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final Path avatarDirectory;

    public ProfileController(JdbcTemplate jdbc, StringRedisTemplate redis, @Value("${app.upload.dir:./data/uploads}") String uploadDirectory) {
        this.jdbc = jdbc; this.redis = redis;
        this.avatarDirectory = Path.of(uploadDirectory, "avatars").toAbsolutePath().normalize();
    }

    public record UpdateProfile(@NotBlank @Size(max = 64) String displayName, @NotBlank String avatarColor) {}
    public record RecentSubmission(String problemTitle, String status, Instant createdAt) {}
    public record Profile(long id, String username, String email, String displayName, String avatarUrl, String avatarColor,
                          Instant createdAt, int solvedCount, int totalSubmissions, int acceptedSubmissions,
                          List<RecentSubmission> recentSubmissions) {}

    @GetMapping
    public ApiResponse<Profile> profile(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(profileFor(userId(jwt)));
    }

    @PutMapping
    public ApiResponse<Profile> update(@Valid @RequestBody UpdateProfile request, @AuthenticationPrincipal Jwt jwt) {
        String color = request.avatarColor().toLowerCase(Locale.ROOT);
        if (!List.of("orange", "green", "blue", "red").contains(color)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像颜色无效");
        }
        jdbc.update("UPDATE users SET display_name=?, avatar_color=? WHERE id=?", request.displayName().trim(), color, userId(jwt));
        evictAccount(userId(jwt));
        return ApiResponse.ok(profileFor(userId(jwt)));
    }

    @PutMapping(value = "/avatar", consumes = "multipart/form-data")
    public ApiResponse<Profile> uploadAvatar(@RequestPart("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        if (file.isEmpty() || file.getSize() > MAX_AVATAR_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件必须小于 2MB");
        }
        String extension = extension(file.getOriginalFilename());
        if (!List.of("png", "jpg", "jpeg", "webp").contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 PNG、JPG 和 WebP 图片");
        }
        try {
            Files.createDirectories(avatarDirectory);
            String filename = UUID.randomUUID() + "." + extension;
            Path target = avatarDirectory.resolve(filename).normalize();
            if (!target.startsWith(avatarDirectory)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像文件无效");
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            jdbc.update("UPDATE users SET avatar_url=? WHERE id=?", "/uploads/avatars/" + filename, userId(jwt));
            evictAccount(userId(jwt));
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "头像保存失败");
        }
        return ApiResponse.ok(profileFor(userId(jwt)));
    }

    private Profile profileFor(long userId) {
        Map<String, Object> user = jdbc.queryForList("SELECT id,username,email,display_name,avatar_url,avatar_color,created_at FROM users WHERE id=?", userId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM submissions WHERE user_id=?", Integer.class, userId);
        int accepted = jdbc.queryForObject("SELECT COUNT(*) FROM submissions WHERE user_id=? AND status='AC'", Integer.class, userId);
        int solved = jdbc.queryForObject("SELECT COUNT(DISTINCT problem_id) FROM submissions WHERE user_id=? AND status='AC'", Integer.class, userId);
        List<RecentSubmission> recent = jdbc.query("""
                SELECT p.title,s.status,s.created_at FROM submissions s JOIN problems p ON p.id=s.problem_id
                WHERE s.user_id=? ORDER BY s.created_at DESC LIMIT 6
                """, (rs, row) -> new RecentSubmission(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()), userId);
        return new Profile(((Number) user.get("id")).longValue(), (String) user.get("username"), (String) user.get("email"),
                (String) user.get("display_name"), (String) user.get("avatar_url"), (String) user.get("avatar_color"),
                ((java.sql.Timestamp) user.get("created_at")).toInstant(), solved, total, accepted, recent);
    }

    private long userId(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        return Long.parseLong(jwt.getSubject());
    }

    private void evictAccount(long userId) { try { redis.delete("codeagent:account:" + userId); } catch (Exception ignored) { } }

    private String extension(String filename) {
        String value = StringUtils.getFilenameExtension(filename);
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
