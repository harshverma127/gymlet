package com.gymlet.service;

import com.gymlet.domain.AppUser;
import com.gymlet.domain.AuthSession;
import com.gymlet.domain.Exercise;
import com.gymlet.domain.WorkoutDay;
import com.gymlet.domain.WorkoutExercise;
import com.gymlet.repository.AppUserRepository;
import com.gymlet.repository.AuthSessionRepository;
import com.gymlet.repository.ExerciseRepository;
import com.gymlet.repository.WorkoutDayRepository;
import com.gymlet.repository.WorkoutExerciseRepository;
import com.gymlet.web.UnauthorizedException;
import com.gymlet.web.dto.AuthDtos;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Username + 4-digit PIN auth.
 *
 * PINs are hashed with BCrypt. Sessions are opaque server-side tokens (30 days)
 * sent by the client as "Authorization: Bearer <token>". New accounts get an
 * independent copy of the default workout template.
 */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{2,30}$");
    private static final Pattern PIN = Pattern.compile("^\\d{4}$");
    private static final long SESSION_DAYS = 30;

    private final AppUserRepository userRepository;
    private final AuthSessionRepository sessionRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutDayRepository workoutDayRepository;
    private final WorkoutExerciseRepository workoutExerciseRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AppUserRepository userRepository,
                       AuthSessionRepository sessionRepository,
                       ExerciseRepository exerciseRepository,
                       WorkoutDayRepository workoutDayRepository,
                       WorkoutExerciseRepository workoutExerciseRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.exerciseRepository = exerciseRepository;
        this.workoutDayRepository = workoutDayRepository;
        this.workoutExerciseRepository = workoutExerciseRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // -------------------------------------------------------------- register

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.AuthRequest req) {
        String username = normalizeUsername(req.username());
        String pin = normalizePin(req.pin());
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPinHash(passwordEncoder.encode(pin));
        user.setName(username);
        userRepository.save(user);
        copyTemplateInto(user);
        return createSession(user);
    }

    // ----------------------------------------------------------------- login

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.AuthRequest req) {
        String username = normalizeUsername(req.username());
        String pin = normalizePin(req.pin());
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("Incorrect username or PIN"));
        if (user.getPinHash() == null || !passwordEncoder.matches(pin, user.getPinHash())) {
            throw new UnauthorizedException("Incorrect username or PIN");
        }
        return createSession(user);
    }

    /** One-time claim of the pre-auth legacy account (existing data kept, PIN chosen now). */
    @Transactional
    public AuthDtos.AuthResponse claim(AuthDtos.AuthRequest req) {
        String username = normalizeUsername(req.username());
        String pin = normalizePin(req.pin());
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("No unclaimed account with that username"));
        if (user.getPinHash() != null) {
            throw new UnauthorizedException("No unclaimed account with that username");
        }
        user.setPinHash(passwordEncoder.encode(pin));
        userRepository.save(user);
        return createSession(user);
    }

    // --------------------------------------------------------------- session

    /** True if the token maps to a live session; used by the auth filter. */
    @Transactional(readOnly = true)
    public AppUser resolveUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        AuthSession session = sessionRepository.findByToken(token).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return session.getUser();
    }

    @Transactional(readOnly = true)
    public AuthDtos.MeDto me(String token) {
        AppUser user = resolveUser(token);
        if (user == null) {
            throw new UnauthorizedException("Session expired");
        }
        return new AuthDtos.MeDto(user.getUsername(), user.getName());
    }

    @Transactional
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessionRepository.findByToken(token).ifPresent(sessionRepository::delete);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthStatusDto status() {
        AppUser legacy = userRepository.findFirstByPinHashIsNullAndUsernameIsNotNull().orElse(null);
        return new AuthDtos.AuthStatusDto(legacy == null ? null : legacy.getUsername());
    }

    private AuthDtos.AuthResponse createSession(AppUser user) {
        sessionRepository.deleteByUserId(user.getId());
        String token = generateToken();
        AuthSession session = new AuthSession();
        session.setToken(token);
        session.setUser(user);
        session.setCreatedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(SESSION_DAYS));
        sessionRepository.save(session);
        return new AuthDtos.AuthResponse(token, user.getUsername(), user.getName());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ------------------------------------------------------------- template

    /** The freshly copied template ids: old template id -> the user's copy. */
    public record TemplateCopy(Map<Long, Exercise> exercises, Map<Long, WorkoutDay> days) {
    }

    /**
     * Copies the shared default split (user_id NULL rows) into the user's
     * account so each account is fully independent.
     */
    @Transactional
    public TemplateCopy copyTemplateInto(AppUser user) {
        Map<Long, Exercise> copied = new HashMap<>();
        for (Exercise t : exerciseRepository.findAllByUserIdIsNull()) {
            Exercise copy = new Exercise();
            copy.setUserId(user.getId());
            copy.setName(t.getName());
            copy.setMuscleGroup(t.getMuscleGroup());
            copy.setRepMin(t.getRepMin());
            copy.setRepMax(t.getRepMax());
            copy.setCompound(t.isCompound());
            copied.put(t.getId(), exerciseRepository.save(copy));
        }
        Map<Long, WorkoutDay> copiedDays = new HashMap<>();
        for (WorkoutDay t : workoutDayRepository.findByUserIdIsNullOrderByDayNumberAsc()) {
            WorkoutDay copy = new WorkoutDay();
            copy.setUserId(user.getId());
            copy.setName(t.getName());
            copy.setDayNumber(t.getDayNumber());
            copy = workoutDayRepository.save(copy);
            copiedDays.put(t.getId(), copy);
            for (WorkoutExercise we : workoutExerciseRepository.findByWorkoutDayIdOrderBySetOrderAsc(t.getId())) {
                WorkoutExercise weCopy = new WorkoutExercise();
                weCopy.setWorkoutDay(copy);
                weCopy.setExercise(copied.get(we.getExercise().getId()));
                weCopy.setSetOrder(we.getSetOrder());
                weCopy.setSets(we.getSets());
                workoutExerciseRepository.save(weCopy);
            }
        }
        return new TemplateCopy(copied, copiedDays);
    }

    // ------------------------------------------------------------ validation

    private String normalizeUsername(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Username is required");
        }
        String username = raw.trim().toLowerCase();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 2–30 characters (letters, digits, dots, dashes, underscores)");
        }
        return username;
    }

    private String normalizePin(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("PIN is required");
        }
        String pin = raw.trim();
        if (!PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("PIN must contain exactly 4 digits");
        }
        return pin;
    }
}
