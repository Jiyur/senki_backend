package com.abc.senki.controller.client;

import com.abc.senki.handler.HttpMessageNotReadableException;
import com.abc.senki.handler.MethodArgumentNotValidException;
import com.abc.senki.handler.RecordNotFoundException;
import com.abc.senki.model.entity.UserEntity;
import com.abc.senki.model.payload.request.UserRequest.AddNewUserRequest;
import com.abc.senki.model.payload.request.UserRequest.ForgetPasswordRequest;
import com.abc.senki.model.payload.request.UserRequest.UserLoginRequest;
import com.abc.senki.model.payload.response.ErrorResponse;
import com.abc.senki.model.payload.response.SuccessResponse;
import com.abc.senki.security.dto.AppUserDetail;
import com.abc.senki.security.jwt.JwtUtils;
import com.abc.senki.service.EmailService;
import com.abc.senki.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;

import static com.abc.senki.common.ErrorDefinition.ERROR_TRY_AGAIN;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/auth/")
public class AuthenticateController {
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String REFRESH_TOKEN = "refreshToken";
    @Autowired
    private UserService userService;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private ModelMapper mapper;
    @Autowired
    private EmailService emailService;

    @PostMapping("register")
    @Operation(summary = "Register new user")
    public ResponseEntity<Object> register(@RequestBody @Valid AddNewUserRequest request) {
        request.setPassword(encoder.encode(request.getPassword()));

        UserEntity userEntity = mapper.map(request, UserEntity.class);
        userEntity.setCreateAt(LocalDateTime.now());

        if (Boolean.TRUE.equals(userService.existsByEmail(userEntity.getEmail()))) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.error("This email already exists", HttpStatus.BAD_REQUEST.value()));
        }
        try {
            userService.saveUser(userEntity, "USER");
            HashMap<String, Object> data = new HashMap<>();
            data.put("user", userEntity.getEmail());
            return ResponseEntity
                    .ok(new SuccessResponse(HttpStatus.OK.value(), "Register successfully", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.error("Your submition failed, please try again later", HttpStatus.BAD_REQUEST.value()));
        }
    }

    @PostMapping("login")
    @Operation(summary = "Login user")
    public ResponseEntity<Object> login(@RequestBody @Valid UserLoginRequest user, HttpServletResponse resp) {
        if (Boolean.FALSE.equals(userService.existsByEmail(user.getEmail()))) {
            return ResponseEntity.badRequest().body(ErrorResponse.error("This email not valid", HttpStatus.BAD_REQUEST.value()));
        }
        UserEntity loginUser = userService.findByEmail(user.getEmail());
        if (!encoder.matches(user.getPassword(), loginUser.getPassword())) {
            return ResponseEntity.badRequest().body(ErrorResponse.error("Wrong password", HttpStatus.BAD_REQUEST.value()));
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginUser.getId().toString(), user.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AppUserDetail userDetail = (AppUserDetail) authentication.getPrincipal();

        String accessToken = generateActiveToken(userDetail);
        String refreshToken = generateRefreshToken(userDetail);
        HashMap<String, Object> data = new HashMap<>();
        data.put(ACCESS_TOKEN, accessToken);
        data.put("refreshToken", refreshToken);
        data.put("user", loginUser);

        resp.setHeader("Set-Cookie", "test=value; Path=/");
        resp.addCookie(new Cookie(ACCESS_TOKEN, accessToken));
        return ResponseEntity.ok(new SuccessResponse(HttpStatus.OK.value(), "Login success", data));

    }

    @GetMapping("/social")
    @Operation(summary = "Login with social account")
    public ResponseEntity<Object> socialToken(
            @RequestParam(defaultValue = "") String token,
            HttpServletResponse resp) {
        try {
            if (token == null || token.equals("")) {
                throw new BadCredentialsException("token is not valid");
            }
            String email = jwtUtils.getUserNameFromJwtToken(token);
            UserEntity user = userService.findByEmail(email);
            if (user == null) {
                throw new RecordNotFoundException("Not found, please register again");
            }
            AppUserDetail userDetails = AppUserDetail.build(user);
            String accessToken = generateActiveToken(userDetails);
            String refreshToken = generateRefreshToken(userDetails);

            HashMap<String, Object> data = new HashMap<>();
            data.put(ACCESS_TOKEN, accessToken);
            data.put(REFRESH_TOKEN, refreshToken);
            data.put("user", user);

            resp.setHeader("Set-Cookie", "test=value; Path=/");
            resp.addCookie(new Cookie(ACCESS_TOKEN, accessToken));
            resp.addCookie(new Cookie("refreshToken", refreshToken));

            return ResponseEntity.ok(new SuccessResponse(HttpStatus.OK.value(), "Login success", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.error(ERROR_TRY_AGAIN.getMessage(), HttpStatus.BAD_REQUEST.value()));
        }
    }

    @PostMapping("/forgetPassword")
    @Operation(summary = "Restore password by email")
    public ResponseEntity<Object> forgetPassword(@RequestBody @Valid ForgetPasswordRequest request, BindingResult errors) throws MethodArgumentNotValidException {
        try {
            if (errors.hasErrors()) {
                throw new MethodArgumentNotValidException(errors);
            }
            if (request == null) {
                throw new HttpMessageNotReadableException("Missing field");
            }
            if (userService.findByEmail(request.getEmail()) == null) {
                throw new HttpMessageNotReadableException("Email is not Registered");
            }
            UserEntity user = userService.findByEmail(request.getEmail());
            emailService.sendForgetPasswordMessage(user);
            HashMap<String, Object> data = new HashMap<>();
            data.put("email", user.getEmail());
            return ResponseEntity.ok(new SuccessResponse(HttpStatus.OK.value(), "Email sent successfully", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.error(e.getMessage(), HttpStatus.BAD_REQUEST.value()));
        }
    }

    public String generateActiveToken(AppUserDetail userDetail) {
        return jwtUtils.generateJwtToken(userDetail);
    }

    public String generateRefreshToken(AppUserDetail userDetail) {
        return jwtUtils.generateRefreshJwtToken(userDetail);
    }
}